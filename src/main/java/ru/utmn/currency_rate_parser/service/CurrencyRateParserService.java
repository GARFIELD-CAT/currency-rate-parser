package ru.utmn.currency_rate_parser.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.utmn.currency_rate_parser.model.*;
import ru.utmn.currency_rate_parser.model.Currency;
import ru.utmn.currency_rate_parser.repository.CurrencyRateRepository;
import ru.utmn.currency_rate_parser.repository.CurrencyRepository;
import ru.utmn.currency_rate_parser.service.aggregator.CurrencyRateTaskProducer;
import ru.utmn.currency_rate_parser.task.LoggerTask;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static ru.utmn.currency_rate_parser.Constants.*;
import static ru.utmn.currency_rate_parser.utils.CoinMarketCapUrlBuilder.buildHistoricalUrl;
import static ru.utmn.currency_rate_parser.utils.TimeUtils.*;

@Service
public class CurrencyRateParserService {
    private static final Logger log = LoggerFactory.getLogger(CurrencyRateParserService.class);

    private final WebClient webClient;
    private final CurrencyRepository currencyRepository;
    private final CurrencyRateRepository currencyRateRepository;
    private final ExecutorService executorService = Executors.newFixedThreadPool(30);
    private final ExecutorService parseCurrencyExecutorService = Executors.newFixedThreadPool(20);
    AtomicInteger demonCount = new AtomicInteger(0);

    public CurrencyRateParserService(WebClient webClient, CurrencyRepository currencyRepository, CurrencyRateRepository currencyRateRepository, CurrencyRateTaskProducer currencyRateTaskProducer) {
        this.webClient = webClient;
        this.currencyRepository = currencyRepository;
        this.currencyRateRepository = currencyRateRepository;
    }

    private CurrencyRate getCurrencyRate(Currency currency, List<HistoryApiResponse.QuoteData> quotes, String baseCurrency) {
        HistoryApiResponse.Quote quote = quotes.get(quotes.size() - 1).getQuote();
        var rate = quote.getClose();
        var change24h = quote.getOpen() - quote.getClose();
        var currencyRateDate = convertStringDateToLocalDate(quote.getTimestamp());

        Optional<CurrencyRate> old_currency = currencyRateRepository.findByCurrencyAndCurrencyRateDateAndBaseCurrency(currency, currencyRateDate, baseCurrency);

        if (old_currency.isPresent()) {
            return new CurrencyRate(
                    old_currency.get().getId(),
                    rate,
                    change24h,
                    currencyRateDate,
                    baseCurrency,
                    LocalDateTime.now(),
                    currency
            );
        } else {
            return new CurrencyRate(
                    rate,
                    change24h,
                    currencyRateDate,
                    baseCurrency,
                    currency
            );
        }
    }

    public List<CurrencyWithRatesDto> findAllCurrencyWithRates(int page, int size) {
        List<String> currencySymbols = currencyRateRepository.findDistinctCurrencyInfo();

        int fromIndex = page * size;

        if (fromIndex >= currencySymbols.size()) {
            log.info("Запрашиваемая страница выходит за пределы доступных данных.");
            return Collections.emptyList();
        }

        int toIndex = Math.min(fromIndex + size, currencySymbols.size());

        List<String> paginatedCurrencySymbols = currencySymbols.subList(fromIndex, toIndex);

        List<CompletableFuture<CurrencyWithRatesDto>> futures = paginatedCurrencySymbols.stream()
                .map(symbol -> CompletableFuture.supplyAsync(() -> prepareCurrencyWithRatesDto(symbol), executorService))
                .filter(Objects::nonNull)
                .toList();

        List<CurrencyWithRatesDto> currencyWithRatesDto = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();

        log.info("{}: Успешно собрал информацию по {} валютам.", Thread.currentThread().getName(), paginatedCurrencySymbols.size());

        return currencyWithRatesDto;
    }

    private CurrencyWithRatesDto prepareCurrencyWithRatesDto(String currencySymbols) {
        log.info("{}: Начинаю сбор информации по валюте {}.", Thread.currentThread().getName(), currencySymbols);

        LocalDate rateDate = LocalDate.now();

        List<CurrencyRate> allCurrencyRatesForDay = currencyRateRepository.findByCurrencySymbolAndCurrencyRateDate(currencySymbols, rateDate);

        if (allCurrencyRatesForDay.isEmpty()) {
            return null;
        }

        CurrencyWithRatesDto currencyWithRatesDto = new CurrencyWithRatesDto();
        CurrencyRate firstElem = allCurrencyRatesForDay.get(0);
        Currency currency = firstElem.getCurrency();

        currencyWithRatesDto.setCurrencyId(currency.getId());
        currencyWithRatesDto.setCurrencyName(currency.getCurrencyName());
        currencyWithRatesDto.setCurrencySymbol(currency.getCurrencySymbol());
        List<CurrencyRateDto> quotes = new ArrayList<>(allCurrencyRatesForDay.size());

        for (var rate : allCurrencyRatesForDay) {
            CurrencyRateDto dto = new CurrencyRateDto();

            dto.setRateId(rate.getId());
            dto.setRate(rate.getRate());
            dto.setChange24h(rate.getChange24h());
            dto.setCurrencyRateDate(rate.getCurrencyRateDate());
            dto.setBaseCurrency(rate.getBaseCurrency());
            dto.setLastUpdated(rate.getLastUpdated());

            quotes.add(dto);
        }

        currencyWithRatesDto.setQuotes(quotes);
        log.info("{}: Успешно закончил сбор информации по валюте {}.", Thread.currentThread().getName(), currencySymbols);

        return currencyWithRatesDto;
    }

    public List<CurrencyRate> parseCurrencyRates(LocalDate parseDay, List<String> currencyNames, Boolean manualParse) {
        log.info("{}: Начинаю асинхронную агрегацию данных по списку валют за {}.", Thread.currentThread().getName(), parseDay);

        long timeStart = convertDateToTimestamp(parseDay);

        List<CompletableFuture<List<CurrencyRate>>> futures = currencyNames.stream()
                .map(name -> CompletableFuture.supplyAsync(() -> {
                    Optional<Currency> currency = currencyRepository.findByCurrencySymbol(name);
                    List<CurrencyRate> results = new ArrayList<>();

                    if (currency.isEmpty()) {
                        log.info("{}: Валюта с именем {} не найдена в базе данных.", Thread.currentThread().getName(), name);
                        return results;
                    }

                    List<CurrencyRate> currencyRates = currencyRateRepository.findByCurrencyAndCurrencyRateDate(currency.get(), parseDay);

                    if (currencyRates.size() == FIAT_CURRENCY_COUNT && !manualParse) {
                        log.info("{}: Курсы для валюты {} за {} найдены в базе данных.", Thread.currentThread().getName(), name, parseDay);
                        return currencyRates;
                    }

                    log.info("{}: Курс для валюты {} за {} не найден в базе данных.", Thread.currentThread().getName(), name, parseDay);

                    List<String> urls = new ArrayList<>(FIAT_CURRENCY_COUNT);
                    if (parseDay.isEqual(LocalDate.now())) {
                        urls.add(buildHistoricalUrl(currency.get().getCoinMarketCapId(), USD_CONVERT_ID));
                        urls.add(buildHistoricalUrl(currency.get().getCoinMarketCapId(), RUB_CONVERT_ID));
                    } else {
                        urls.add(buildHistoricalUrl(currency.get().getCoinMarketCapId(), timeStart, USD_CONVERT_ID));
                        urls.add(buildHistoricalUrl(currency.get().getCoinMarketCapId(), timeStart, RUB_CONVERT_ID));
                    }

                    for (String url : urls) {
                        String baseCurrency = url.contains(RUB_CONVERT_ID) ? "RUB" : "USD";
                        Optional<CurrencyRate> currencyRate = Optional.ofNullable(fetchCurrencyRateData(url, currency.get(), baseCurrency));

                        if (currencyRate.isPresent()) {
                            currencyRateRepository.save(currencyRate.get());
                            log.info("{} Курс для валюты {} за {} успешно сохранен в базе данных.", Thread.currentThread().getName(), name, parseDay);
                            results.add(currencyRate.get());
                        }
                    }
                    return results;
                }))
                .toList();

        List<CurrencyRate> combinedResults = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        return combinedResults;
    }

    private CurrencyRate fetchCurrencyRateData(String url, Currency currency, String baseCurrency) {
        long startTime = System.nanoTime();
        ObjectMapper objectMapper = new ObjectMapper();

        try {
            return (CurrencyRate) webClient.get()
                    .uri(url)
                    .exchangeToMono(response -> {
                        if (response.statusCode().is2xxSuccessful()) {
                            evaluateExecutionTime(startTime);

                            return response.bodyToMono(String.class)
                                    .map(body -> {
                                        try {
                                            HistoryApiResponse result = objectMapper.readValue(body, HistoryApiResponse.class);
                                            var data = result.getData();
                                            var quotes = data.getQuotes();

                                            if (quotes.isEmpty()) {
                                                return null;
                                            }

                                            CurrencyRate currencyRate = getCurrencyRate(currency, quotes, baseCurrency);

                                            return currencyRate;
                                        } catch (Exception e) {
                                            return Mono.error(e);
                                        }
                                    });
                        } else {
                            evaluateExecutionTime(startTime);

                            return response.bodyToMono(String.class)
                                    .map(errorBody -> {
                                        log.error("Ошибка при скачивании курсов валют: {}.", errorBody);

                                        return null;
                                    });
                        }
                    })
                    .block();
        } catch (Exception e) {
            evaluateExecutionTime(startTime);
            log.error("Ошибка при скачивании курсов валют: {}.", e.toString());

            return null;
        }
    }

    public Page<Currency> getAllCurrency(Pageable pageable) {
        return currencyRepository.findAll(pageable);
    }

    @PostConstruct
    public String processStartDemon() {
        LoggerTask loggerTask = new LoggerTask(currencyRateRepository, currencyRepository);
        Thread loggerThread = new Thread(loggerTask, "LoggerTask-Daemon:" + demonCount.incrementAndGet());
        loggerThread.setDaemon(true);
        loggerThread.start();

        return "ok!";
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
        parseCurrencyExecutorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
            if (!parseCurrencyExecutorService.awaitTermination(10, TimeUnit.SECONDS)) {
                parseCurrencyExecutorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            parseCurrencyExecutorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

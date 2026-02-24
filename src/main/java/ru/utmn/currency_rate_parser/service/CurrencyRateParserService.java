package ru.utmn.currency_rate_parser.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;
import jakarta.annotation.PostConstruct;
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
import ru.utmn.currency_rate_parser.task.LoggerTask;
import ru.utmn.currency_rate_parser.utils.RoundUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
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
    private final ObjectMapper objectMapper;
    private final Timer parsingTimer;
    private final Counter successCounter;
    private final Counter errorCounter;
    private final Gauge currencyRateDBSize;
    private final CurrencyLockManager lockManager;
    private final Tracer tracer;
    AtomicInteger demonCount = new AtomicInteger(0);

    public CurrencyRateParserService(
            WebClient webClient,
            CurrencyRepository currencyRepository,
            CurrencyRateRepository currencyRateRepository,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            CurrencyLockManager lockManager,
            Tracer tracer
    ) {
        this.webClient = webClient;
        this.currencyRepository = currencyRepository;
        this.currencyRateRepository = currencyRateRepository;
        this.objectMapper = objectMapper;
        this.successCounter = Counter.builder("client.currency_rate.requests.success.count")
                .description("Количество успешных парсингов курсов валют")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("client.currency_rate.requests.error.count")
                .description("Количество ошибок парсинга курсов валют")
                .register(meterRegistry);
        this.parsingTimer = Timer.builder("client.currency_rate.requests.parsing.time")
                .description("Время парсинга курсов валют в секундах")
                .register(meterRegistry);
        this.currencyRateDBSize = Gauge.builder("client.currency_rate.db.size", this::getCurrencyRateDBSize)
                .description("Количество курсов валют в базе данных")
                .register(meterRegistry);
        this.lockManager = lockManager;
        this.tracer = tracer;
    }

    private CurrencyRate getCurrencyRate(Currency currency, List<HistoryApiResponse.QuoteData> quotes, String baseCurrency) {
        HistoryApiResponse.Quote quote = quotes.get(quotes.size() - 1).getQuote();
        var rate = RoundUtils.roundDouble(quote.getClose());
        var change24h = RoundUtils.roundDouble(quote.getOpen() - quote.getClose());
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
        Span childSpan = tracer.spanBuilder("currencyRateParserService.findAllCurrencyWithRates")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();

        try (var scope = childSpan.makeCurrent()) {
            List<String> allCurrencySymbols = currencyRateRepository.findDistinctCurrencyInfo();

            int fromIndex = page * size;

            if (fromIndex >= allCurrencySymbols.size()) {
                log.info("Запрашиваемая страница выходит за пределы доступных данных.");
                return Collections.emptyList();
            }

            int toIndex = Math.min(fromIndex + size, allCurrencySymbols.size());
            List<String> paginatedCurrencySymbols = allCurrencySymbols.subList(fromIndex, toIndex);

            if (paginatedCurrencySymbols.isEmpty()) {
                return Collections.emptyList();
            }

            LocalDate rateDate = LocalDate.now();

            List<CurrencyRate> allRatesForPage = currencyRateRepository.findByCurrencySymbolsAndCurrencyRateDate(
                    paginatedCurrencySymbols,
                    rateDate
            );

            log.info("{}: Загружено {} записей о курсах для {} символов.",
                    Thread.currentThread().getName(),
                    allRatesForPage.size(),
                    paginatedCurrencySymbols.size());

            Map<String, List<CurrencyRate>> groupedBySymbol = allRatesForPage.stream()
                    .collect(Collectors.groupingBy(rate -> rate.getCurrency().getCurrencySymbol()));

            List<CurrencyWithRatesDto> resultDto = paginatedCurrencySymbols.stream()
                    .map(symbol -> {
                        List<CurrencyRate> ratesForSymbol = groupedBySymbol.get(symbol);

                        if (ratesForSymbol == null || ratesForSymbol.isEmpty()) {
                            return null;
                        }

                        return transformToDto(symbol, ratesForSymbol);
                    })
                    .filter(Objects::nonNull)
                    .toList();

            log.info("{}: Успешно собрал информацию по {} валютам.", Thread.currentThread().getName(), resultDto.size());

            return resultDto;
        } catch (Exception e) {
            childSpan.recordException(e);
            childSpan.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            childSpan.end();
        }
    }


    private CurrencyWithRatesDto transformToDto(String symbol, List<CurrencyRate> allCurrencyRatesForDay) {
        Span childSpan = tracer.spanBuilder("currencyRateParserService.transformToDto")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();

        try (var scope = childSpan.makeCurrent()) {

            CurrencyRate firstElem = allCurrencyRatesForDay.get(0);
            Currency currency = firstElem.getCurrency();

            CurrencyWithRatesDto currencyWithRatesDto = new CurrencyWithRatesDto();
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
            log.info("Сбор информации по валюте {} закончен.", symbol);

            return currencyWithRatesDto;
        } catch (Exception e) {
            childSpan.recordException(e);
            childSpan.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            childSpan.end();
        }
    }

    public List<CurrencyRate> parseCurrencyRates(LocalDate parseDay, List<String> currencyNames, Boolean manualParse) {
        Span childSpan = tracer.spanBuilder("currencyRateParserService.parseCurrencyRates")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();

        try (var scope = childSpan.makeCurrent()) {

            log.info("{}: Начинаю асинхронную агрегацию данных по списку валют за {}.", Thread.currentThread().getName(), parseDay);
            long startTime = convertDateToTimestamp(parseDay);
            List<CompletableFuture<List<CurrencyRate>>> futures = currencyNames.stream()
                    .map(name -> CompletableFuture.supplyAsync(() -> {
                        Optional<Currency> currency = currencyRepository.findByCurrencySymbol(name);
                        List<CurrencyRate> results = new ArrayList<>();

                        if (currency.isEmpty()) {
                            log.info("{}: Валюта с именем {} не найдена в базе данных.", Thread.currentThread().getName(), name);
                            errorCounter.increment();

                            return results;
                        }

                        List<CurrencyRate> currencyRates = currencyRateRepository.findByCurrencyIdAndCurrencyRateDate(currency.get().getId(), parseDay);

                        if (currencyRates.size() == FIAT_CURRENCY_COUNT && !manualParse) {
                            log.info("{}: Курсы для валюты {} за {} найдены в базе данных.", Thread.currentThread().getName(), name, parseDay);
                            successCounter.increment();

                            return currencyRates;
                        }

                        log.info("{}: Курс для валюты {} за {} не найден в базе данных.", Thread.currentThread().getName(), name, parseDay);

                        List<String> urls = new ArrayList<>(FIAT_CURRENCY_COUNT);
                        if (parseDay.isEqual(LocalDate.now())) {
                            urls.add(buildHistoricalUrl(currency.get().getCoinMarketCapId(), USD_CONVERT_ID));
                            urls.add(buildHistoricalUrl(currency.get().getCoinMarketCapId(), RUB_CONVERT_ID));
                        } else {
                            urls.add(buildHistoricalUrl(currency.get().getCoinMarketCapId(), startTime, USD_CONVERT_ID));
                            urls.add(buildHistoricalUrl(currency.get().getCoinMarketCapId(), startTime, RUB_CONVERT_ID));
                        }

                        List<CompletableFuture<CurrencyRate>> futuresCurrencyRate = urls.stream()
                                .map(url -> CompletableFuture.supplyAsync(() -> {
                                    String baseCurrency = url.contains(RUB_CONVERT_ID) ? "RUB" : "USD";
                                    return fetchCurrencyRateData(url, currency.get(), baseCurrency);
                                }))
                                .toList();

                        List<CompletableFuture<CurrencyRate>> results1 = futuresCurrencyRate.stream()
                                .map(future -> future.thenApply(currencyRate -> {
                                    if (currencyRate != null) {
                                        saveCurrencyRate(currency.get().getCurrencySymbol(), currencyRate);
                                        log.info("{} Курс для валюты {} за {} успешно сохранен в базе данных.", Thread.currentThread().getName(), name, parseDay);
                                        successCounter.increment();
                                    }
                                    return currencyRate;
                                }))
                                .toList();

                        return results1.stream()
                                .map(CompletableFuture::join)
                                .collect(Collectors.toList());
                    }))
                    .toList();

            List<CurrencyRate> combinedResults = futures.stream()
                    .map(CompletableFuture::join)
                    .flatMap(List::stream)
                    .collect(Collectors.toList());

            parsingTimer.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);

            return combinedResults;
        } catch (Exception e) {
            childSpan.recordException(e);
            childSpan.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            childSpan.end();
        }
    }

    private CurrencyRate fetchCurrencyRateData(String url, Currency currency, String baseCurrency) {
        Span childSpan = tracer.spanBuilder("currencyRateParserService.fetchCurrencyRateData")
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute("http-url", url)
                .startSpan();

        try (var scope = childSpan.makeCurrent()) {
            long startTime = System.nanoTime();

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
                                                childSpan.setStatus(StatusCode.OK);

                                                return currencyRate;
                                            } catch (Exception e) {
                                                return Mono.error(e);
                                            }
                                        });
                            } else {
                                evaluateExecutionTime(startTime);
                                errorCounter.increment();
                                parsingTimer.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);

                                return response.bodyToMono(String.class)
                                        .map(errorBody -> {
                                            log.error("Ошибка при скачивании курсов валют: {}.", errorBody);
                                            errorCounter.increment();
                                            childSpan.setStatus(StatusCode.ERROR, "HTTP" + response.statusCode());

                                            return null;
                                        });
                            }
                        })
                        .block();
            } catch (Exception e) {
                evaluateExecutionTime(startTime);
                log.error("Ошибка при скачивании курсов валют: {}.", e.toString());
                errorCounter.increment();
                parsingTimer.record(System.nanoTime() - startTime, TimeUnit.NANOSECONDS);
                childSpan.setStatus(StatusCode.ERROR, e.getMessage());

                return null;
            }
        } catch (Exception e) {
            childSpan.recordException(e);
            childSpan.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            childSpan.end();
        }
    }

    public Page<Currency> getAllCurrency(Pageable pageable) {
        Span childSpan = tracer.spanBuilder("currencyRateParserService.getAllCurrency")
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();

        try (var scope = childSpan.makeCurrent()) {
            return currencyRepository.findAll(pageable);

        } catch (Exception e) {
            childSpan.recordException(e);
            childSpan.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            childSpan.end();
        }
    }

    @PostConstruct
    public String processStartDemon() {
        LoggerTask loggerTask = new LoggerTask(currencyRateRepository, currencyRepository);
        Thread loggerThread = new Thread(loggerTask, "LoggerTask-Daemon:" + demonCount.incrementAndGet());
        loggerThread.setDaemon(true);
        loggerThread.start();

        return "ok!";
    }

    public long getCurrencyRateDBSize() {
        return currencyRateRepository.count();
    }

    public void saveCurrencyRate(String currencySymbol, CurrencyRate newRate) {
        Lock currencyLock = lockManager.getLockForCurrency(currencySymbol);

        currencyLock.lock();
        try {
            currencyRateRepository.save(newRate);
        } finally {
            currencyLock.unlock();
        }
    }
}

package ru.utmn.currency_rate_parser.service.aggregator;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.utmn.currency_rate_parser.model.Currency;
import ru.utmn.currency_rate_parser.model.CurrencyRate;
import ru.utmn.currency_rate_parser.model.ListingApiResponse;
import ru.utmn.currency_rate_parser.repository.CurrencyRateRepository;
import ru.utmn.currency_rate_parser.repository.CurrencyRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static ru.utmn.currency_rate_parser.Constants.CURRENCY_RATE_TASK_WORKERS_COUNT;
import static ru.utmn.currency_rate_parser.utils.TimeUtils.evaluateExecutionTime;

@Component
public class CurrencyRateTaskWorker {
    private static final Logger log = LoggerFactory.getLogger(CurrencyRateTaskWorker.class);

    private final CurrencyRateTaskProducer currencyRateTaskProducer;
    private final CurrencyRateRepository currencyRateRepository;
    private final CurrencyRepository currencyRepository;
    private final ExecutorService executorService;
    private final WebClient webClient;

    public CurrencyRateTaskWorker(CurrencyRateTaskProducer currencyRateTaskProducer, CurrencyRateRepository currencyRateRepository, CurrencyRepository currencyRepository, WebClient webClient) {
        this.currencyRateTaskProducer = currencyRateTaskProducer;
        this.currencyRateRepository = currencyRateRepository;
        this.currencyRepository = currencyRepository;
        this.executorService = Executors.newFixedThreadPool(CURRENCY_RATE_TASK_WORKERS_COUNT);
        this.webClient = webClient;
    }

    @PostConstruct
    public void startWorker() {
        for (int i = 0; i < CURRENCY_RATE_TASK_WORKERS_COUNT; i++) {
            executorService.submit(this::processTask);
        }
    }

    private void processTask() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                log.info("{}: Ожидаем url", Thread.currentThread().getName());
                var task = currencyRateTaskProducer.take();
                log.info("{}: Url был получен из очереди, url={}", Thread.currentThread().getName(), task.getUrl());

                ListingApiResponse listingApiResponse = fetchData(task);
                if (listingApiResponse != null) {
                    saveListingsData(listingApiResponse);
                }

                log.info("{}: Url {} был успешно обработан.", Thread.currentThread().getName(), task.getUrl());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("{}: Ошибка при скачивании урла {}", Thread.currentThread().getName(), e.getMessage());
            }
        }
    }

    private void saveListingsData(ListingApiResponse listingApiResponse) {
        var data = listingApiResponse.getData();
        var currencyList = data.getCryptoCurrencyList();

        for (var raw_currency : currencyList) {
            Optional<Currency> currency = currencyRepository.findByCurrencySymbol(raw_currency.getSymbol());
            Currency new_currency = new Currency(raw_currency.getId(), raw_currency.getName(), raw_currency.getSymbol());

            if (currency.isEmpty()) {
                log.info("{}: Валюта с именем {} не найдена в базе данных.", Thread.currentThread().getName(), raw_currency.getSymbol());
                currencyRepository.save(new_currency);
                currency = Optional.of(new_currency);
                log.info("{}: Валюта с именем {} успешно создана.", Thread.currentThread().getName(), currency.get().getCurrencySymbol());
            } else {
                if (!new_currency.getCoinMarketCapId().equals(currency.get().getCoinMarketCapId())) {
                    continue;
                }
                log.info("{}: Валюта с именем {} успешно получена из базы.", Thread.currentThread().getName(), currency.get().getCurrencySymbol());
            }

            for (var quote : raw_currency.getQuotes()) {
                saveCurrencyRate(currency.get(), quote, quote.getName());
            }

            log.info("{}: Валюта с именем {} успешно обработана.", Thread.currentThread().getName(), raw_currency.getSymbol());
        }
    }

    private void saveCurrencyRate(Currency currency, ListingApiResponse.Quote quote, String baseCurrency) {
        var rate = quote.getPrice();
        var change24h = quote.getPrice() / 100 * quote.getPercentChange24h();
        var currencyRateDate = LocalDate.now();

        Optional<CurrencyRate> old_currency = currencyRateRepository.findByCurrencyAndCurrencyRateDateAndBaseCurrency(currency, currencyRateDate, baseCurrency);

        if (old_currency.isPresent()) {
            currencyRateRepository.save(
                    new CurrencyRate(
                            old_currency.get().getId(),
                            rate,
                            change24h,
                            currencyRateDate,
                            baseCurrency,
                            LocalDateTime.now(),
                            currency
                    )
            );
            log.info("{}: Курс валюты с именем {} в {} успешно обновлен.", Thread.currentThread().getName(), currency.getCurrencySymbol(), baseCurrency);
        } else {
            currencyRateRepository.save(
                    new CurrencyRate(
                            rate,
                            change24h,
                            currencyRateDate,
                            baseCurrency,
                            currency
                    )
            );
            log.info("{}: Курс валюты с именем {} в {} успешно создан.", Thread.currentThread().getName(), currency.getCurrencySymbol(), baseCurrency);
        }
    }


    private ListingApiResponse fetchData(CurrencyRateTask task) {
        long startTime = System.nanoTime();
        ObjectMapper objectMapper = new ObjectMapper();
        var url = task.getUrl();

        try {
            return (ListingApiResponse) webClient.get()
                    .uri(url)
                    .exchangeToMono(response -> {
                        if (response.statusCode().is2xxSuccessful()) {
                            evaluateExecutionTime(startTime);

                            return response.bodyToMono(String.class)
                                    .map(body -> {
                                        try {
                                            return objectMapper.readValue(body, ListingApiResponse.class);
                                        } catch (Exception e) {
                                            return Mono.error(e);
                                        }
                                    });
                        } else {
                            evaluateExecutionTime(startTime);

                            return response.bodyToMono(String.class)
                                    .map(errorBody -> {
                                        log.error("{} Ошибка при скачивании курсов валют: {}.", Thread.currentThread().getName(), errorBody);

                                        return null;
                                    });
                        }
                    })
                    .block();
        } catch (Exception e) {
            evaluateExecutionTime(startTime);
            log.error("{} Ошибка при скачивании курсов валют: {}.", Thread.currentThread().getName(), e.toString());

            return null;
        }
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            throw new RuntimeException(e);
        }
    }
}

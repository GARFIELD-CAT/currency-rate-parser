package ru.utmn.currency_rate_parser.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.utmn.currency_rate_parser.model.Currency;
import ru.utmn.currency_rate_parser.model.CurrencyRate;
import ru.utmn.currency_rate_parser.model.HistoryApiResponse;
import ru.utmn.currency_rate_parser.repository.CurrencyRateRepository;
import ru.utmn.currency_rate_parser.repository.CurrencyRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static ru.utmn.currency_rate_parser.utils.CoinMarketCapUrlBuilder.buildHistoricalUrl;
import static ru.utmn.currency_rate_parser.utils.TimeUtils.*;

@Service
public class CurrencyRateParserService {
    private static final Logger log = LoggerFactory.getLogger(CurrencyRateParserService.class);

    private final WebClient webClient;
    private final CurrencyRepository currencyRepository;
    private final CurrencyRateRepository currencyRateRepository;

    public CurrencyRateParserService(WebClient webClient, CurrencyRepository currencyRepository, CurrencyRateRepository currencyRateRepository) {
        this.webClient = webClient;
        this.currencyRepository = currencyRepository;
        this.currencyRateRepository = currencyRateRepository;
    }

    private static CurrencyRate getCurrencyRate(Currency currency, List<HistoryApiResponse.QuoteData> quotes) {
        HistoryApiResponse.Quote quote = quotes.get(quotes.size() - 1).getQuote();
        var value = quote.getClose();
        var change24h = quote.getOpen() - quote.getClose();
        var currencyRateDate = convertStringDateToLocalDate(quote.getTimestamp());

        return new CurrencyRate(
                value,
                change24h,
                currencyRateDate,
                currency
        );
    }

    public Page<CurrencyRate> findAll(Pageable pageable) {
        return null;
    }

    public List<Optional<CurrencyRate>> parseCurrencyRates(LocalDate parseDay, List<String> currencyNames) {
        log.info("Начинаю асинхронную агрегацию данных по списку валют за {}.", parseDay);
        // Можно задачи еще разибвать на отдельные валюты и тогда вообще быстро все будет.

        long timeStart = convertDateToTimestamp(parseDay);
        List<Optional<CurrencyRate>> result = new ArrayList<>(currencyNames.size());

        for (String name : currencyNames) {
            Optional<Currency> currency = currencyRepository.findByCurrencySymbol(name);

            if (currency.isEmpty()) {
                log.info("Валюта с именем {} не найдена в базе данных.", name);
//                result.add(String.format("Валюта с именем %s не найдена в базе данных.", name));
//                ???? Вызывать ошибку
//                raise new CurrencyNotFoundError(String.format("Валюта с именем %s не найдена в базе данных.", name))

                continue;
            }

            Optional<CurrencyRate> currencyRate = currencyRateRepository.findByCurrencyAndCurrencyRateDate(currency.get(), parseDay);

            if (currencyRate.isPresent()) {
                log.info("Курс для валюты {} за {} найден в базе данных.", name, parseDay);
                result.add(currencyRate);

                continue;
            }
            log.info("Курс для валюты {} за {} не найден в базе данных.", name, parseDay);

            String url;

            if (parseDay.isEqual(LocalDate.now())) {
                url = buildHistoricalUrl(currency.get().getCoinMarketCapId());
            } else {
                url = buildHistoricalUrl(currency.get().getCoinMarketCapId(), timeStart);
            }


//            Тут нужно будет таску кидать в очередь, а воркеры будут их разгребать!
            currencyRate = Optional.ofNullable(fetchCurrencyRateData(url, currency.get()));

            result.add(currencyRate);
        }

        return result;
    }

    private CurrencyRate fetchCurrencyRateData(String url, Currency currency) {
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

                                            CurrencyRate currencyRate = getCurrencyRate(currency, quotes);

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
}

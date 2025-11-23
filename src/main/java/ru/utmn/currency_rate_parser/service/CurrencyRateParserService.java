package ru.utmn.currency_rate_parser.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.utmn.currency_rate_parser.model.*;
import ru.utmn.currency_rate_parser.repository.CurrencyRateRepository;
import ru.utmn.currency_rate_parser.repository.CurrencyRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static ru.utmn.currency_rate_parser.Constants.RUB_CONVERT_ID;
import static ru.utmn.currency_rate_parser.Constants.USD_CONVERT_ID;
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

    private static CurrencyRate getCurrencyRate(Currency currency, List<HistoryApiResponse.QuoteData> quotes, String baseCurrency) {
        HistoryApiResponse.Quote quote = quotes.get(quotes.size() - 1).getQuote();
        var rate = quote.getClose();
        var change24h = quote.getOpen() - quote.getClose();
        var currencyRateDate = convertStringDateToLocalDate(quote.getTimestamp());

        return new CurrencyRate(
                rate,
                change24h,
                currencyRateDate,
                baseCurrency,
                currency
        );
    }

    public List<CurrencyWithRatesDto> findAllCurrencyWithRates(Pageable pageable) {
        List<String> currencySymbols = currencyRateRepository.findDistinctCurrencyInfo();

//       Тут можно таски создать отдельные для получения курсов валют по конкретной криптовалюте.
        List<CurrencyWithRatesDto> currencyWithRatesDto = currencySymbols
                .parallelStream()
                .map(this::prepareCurrencyWithRatesDto)
                .filter(Objects::nonNull)
                .toList();

        return currencyWithRatesDto;
    }

    private CurrencyWithRatesDto prepareCurrencyWithRatesDto(String currencySymbols) {
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

        return currencyWithRatesDto;
    }

    public List<CurrencyRate> parseCurrencyRates(LocalDate parseDay, List<String> currencyNames) {
        log.info("Начинаю асинхронную агрегацию данных по списку валют за {}.", parseDay);
        // Можно задачи еще разибвать на отдельные валюты и тогда вообще быстро все будет.

        long timeStart = convertDateToTimestamp(parseDay);
        List<CurrencyRate> result = new ArrayList<>(currencyNames.size());

        for (String name : currencyNames) {
            Optional<Currency> currency = currencyRepository.findByCurrencySymbol(name);

            if (currency.isEmpty()) {
                log.info("Валюта с именем {} не найдена в базе данных.", name);
//                result.add(String.format("Валюта с именем %s не найдена в базе данных.", name));
//                ???? Вызывать ошибку или пытаться скачать? Но как?
//                raise new CurrencyNotFoundError(String.format("Валюта с именем %s не найдена в базе данных.", name))

                continue;
            }

            List<CurrencyRate> currencyRates = currencyRateRepository.findByCurrencyAndCurrencyRateDate(currency.get(), parseDay);

            if (!currencyRates.isEmpty()) {
                log.info("Курсы для валюты {} за {} найдены в базе данных.", name, parseDay);
                result.addAll(currencyRates);

                continue;
            }
            log.info("Курс для валюты {} за {} не найден в базе данных.", name, parseDay);

            List<String> urls = new ArrayList<>(2);

            if (parseDay.isEqual(LocalDate.now())) {
                urls.add(buildHistoricalUrl(currency.get().getCoinMarketCapId(), USD_CONVERT_ID));
                urls.add(buildHistoricalUrl(currency.get().getCoinMarketCapId(), RUB_CONVERT_ID));
            } else {
                urls.add(buildHistoricalUrl(currency.get().getCoinMarketCapId(), timeStart, USD_CONVERT_ID));
                urls.add(buildHistoricalUrl(currency.get().getCoinMarketCapId(), timeStart, RUB_CONVERT_ID));
            }

//            Тут нужно будет таску кидать в очередь, а воркеры будут их разгребать!
            for (var url : urls) {
                String baseCurrency = "USD";

                if (url.contains(RUB_CONVERT_ID)) {
                    baseCurrency = "RUB";
                }

                Optional<CurrencyRate> currencyRate = Optional.ofNullable(fetchCurrencyRateData(url, currency.get(), baseCurrency));
                log.info("Курс для валюты {} за {} успешно скачан с источника.", name, parseDay);

                if (currencyRate.isPresent()) {
                    currencyRateRepository.save(currencyRate.get());
                    log.info("Курс для валюты {} за {} успешно сохранен в базе данных.", name, parseDay);

                    result.add(currencyRate.get());
                }
            }
        }

        return result;
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
}

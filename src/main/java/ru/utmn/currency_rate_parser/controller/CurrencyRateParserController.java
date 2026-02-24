package ru.utmn.currency_rate_parser.controller;


import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.utmn.currency_rate_parser.model.Currency;
import ru.utmn.currency_rate_parser.model.CurrencyHistoryRatesRequestBody;
import ru.utmn.currency_rate_parser.model.CurrencyRate;
import ru.utmn.currency_rate_parser.model.CurrencyWithRatesDto;
import ru.utmn.currency_rate_parser.service.CurrencyRateParserService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/currency-rates")
public class CurrencyRateParserController {
    @Autowired
    private Tracer tracer;

    @Autowired
    private CurrencyRateParserService currencyRateParserService;

    private static final Logger log = LoggerFactory.getLogger(CurrencyRateParserController.class);

    @Operation(summary = "Получить актуальные курсы валют за сегодня")
    @GetMapping("/get-all")
    public ResponseEntity<List<CurrencyWithRatesDto>> getAllCurrencyRates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        Span rootSpan = tracer.spanBuilder("GET /api/v1/currency-rates/get-all")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();

        try(var scope = rootSpan.makeCurrent()){
            List<CurrencyWithRatesDto> currencyWithRates = currencyRateParserService.findAllCurrencyWithRates(page, size);
            rootSpan.setAttribute("response.currencyWithRatesSize", currencyWithRates.size());

            return ResponseEntity.ok(currencyWithRates);
        } catch (Exception e ){
            rootSpan.recordException(e);
            rootSpan.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            rootSpan.end();
        }
    }

    @Operation(summary = "Скачать курсы валют за определенный день", description = "Может работать медленно из-за скачивания актуальных данных")
    @PostMapping("/parse")
    public ResponseEntity<?> parseCurrencyRates(@RequestBody CurrencyHistoryRatesRequestBody body) {
        LocalDate date;
        Span rootSpan = tracer.spanBuilder("POST /api/v1/currency-rates/parse")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();

        log.info("Root Span started: {}", rootSpan.getSpanContext());

        try {
            date = LocalDate.parse(body.getParseDate(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body("Неправильный формат даты. Используйте YYYY-MM-DD.");
        }

        List<String> uniqueList = body.getCurrencySymbols()
                .parallelStream()
                .distinct()
                .limit(100)
                .filter(Objects::nonNull)
                .toList();

        try(var scope = rootSpan.makeCurrent()){
            List<CurrencyRate> currencyRates = currencyRateParserService.parseCurrencyRates(date, uniqueList, body.getManualParse());
            rootSpan.setAttribute("response.currencyRatesSize", currencyRates.size());

            return ResponseEntity.ok(currencyRates);
        } catch (Exception e ){
            rootSpan.recordException(e);
            rootSpan.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            log.info("Root Span ended: {}", rootSpan.getSpanContext());
            rootSpan.end();
        }
    }

    @Operation(summary = "Получить список всех криптовалют, доступных для скачивания", description = "Есть пагинация и сортировка")
    @GetMapping("/get-all-currency")
    public ResponseEntity<List<Currency>> getAllCurrency(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "coinMarketCapId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir
    ) {
        Span rootSpan = tracer.spanBuilder("GET /api/v1/currency-rates/get-all-currency")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();

        Sort sort;
        if (sortDir.equalsIgnoreCase("asc")) {
            sort = Sort.by(sortBy).ascending();
        } else {
            sort = Sort.by(sortBy).descending();
        }

        Pageable pageable = PageRequest.of(page, size, sort);

        try(var scope = rootSpan.makeCurrent()){
            Page<Currency> currencyPage = currencyRateParserService.getAllCurrency(pageable);

            List<Currency> currencies = currencyPage.getContent()
                    .parallelStream()
                    .filter(Objects::nonNull)
                    .toList();

            rootSpan.setAttribute("response.currenciesSize", currencies.size());

            return ResponseEntity.ok(currencies);

        } catch (Exception e ){
            rootSpan.recordException(e);
            rootSpan.setStatus(StatusCode.ERROR);
            throw e;
        } finally {
            rootSpan.end();
        }
    }
}

package ru.utmn.currency_rate_parser.controller;


import io.swagger.v3.oas.annotations.Operation;
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
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/currency-rates")
public class CurrencyRateParserController {
    @Autowired
    private CurrencyRateParserService currencyRateParserService;

    @Operation(summary = "Получить актуальные курсы валют за сегодня")
    @GetMapping("/get-all")
    public ResponseEntity<List<CurrencyWithRatesDto>> getAllCurrencyRates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        List<CurrencyWithRatesDto> currencyWithRates = currencyRateParserService.findAllCurrencyWithRates(page, size);

        return ResponseEntity.ok(currencyWithRates);
    }

    @Operation(summary = "Скачать курсы валют за определенный день", description = "Может работать медленно из-за скачивания актуальных данных")
    @PostMapping("/parse")
    public ResponseEntity<List<CurrencyRate>> parseCurrencyRates(@RequestBody CurrencyHistoryRatesRequestBody body) {
        LocalDate date = LocalDate.parse(body.getParseDate(), DateTimeFormatter.ISO_LOCAL_DATE);

        List<String> uniqueList = body.getCurrencySymbols()
                .parallelStream()
                .distinct()
                .toList();

        List<CurrencyRate> message = currencyRateParserService.parseCurrencyRates(date, uniqueList, body.getManualParse());
        return ResponseEntity.ok(message);
    }

    @Operation(summary = "Получить список всех криптовалют, доступных для скачивания", description = "Есть пагинация и сортировка")
    @PostMapping("/get-all-currency")
    public ResponseEntity<List<Currency>> getAllCurrency(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "coinMarketCapId") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir
    ) {

        Sort sort;
        if (sortDir.equalsIgnoreCase("asc")) {
            sort = Sort.by(sortBy).ascending();
        } else {
            sort = Sort.by(sortBy).descending();
        }

        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Currency> currencyPage = currencyRateParserService.getAllCurrency(pageable);

        List<Currency> resultList = currencyPage.getContent()
                .parallelStream()
                .filter(Objects::nonNull)
                .toList();

        return ResponseEntity.ok(resultList);
    }
}

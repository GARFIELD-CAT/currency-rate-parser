package ru.utmn.currency_rate_parser.controller;


import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.utmn.currency_rate_parser.model.CurrencyRate;
import ru.utmn.currency_rate_parser.model.CurrencyHistoryRatesRequestBody;
import ru.utmn.currency_rate_parser.service.CurrencyRateParserService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/currency-rates")
public class CurrencyRateParserController {
    @Autowired
    private CurrencyRateParserService currencyRateParserService;

    @Operation(summary = "Получить актуальные курсы валют", description = "Есть пагинация и сортировка")
    @GetMapping("/get-all")
    public ResponseEntity<List<CurrencyRate>> getCurrencyRates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "currencyName") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDir) {

        Sort sort = sortDir.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<CurrencyRate> currencyRatePage = currencyRateParserService.findAll(pageable);

        List<CurrencyRate> resultList = currencyRatePage.getContent()
                .parallelStream()
                .collect(Collectors.toList());

        return ResponseEntity.ok(resultList);
    }

    @Operation(summary = "Скачать курсы валют за определенный день", description = "Может работать медленно из-за скачивания актуальных данных")
    @PostMapping("/parse")
    public ResponseEntity<List<CurrencyRate>> parseCurrencyRates(@RequestBody CurrencyHistoryRatesRequestBody body) {
        LocalDate date = LocalDate.parse(body.getParseDate(), DateTimeFormatter.ISO_LOCAL_DATE);

        List<CurrencyRate> message = currencyRateParserService.parseCurrencyRates(date, body.getCurrencyNames());
        return ResponseEntity.ok(message);
    }
}

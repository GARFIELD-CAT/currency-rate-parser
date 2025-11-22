package ru.utmn.currency_rate_parser.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CurrencyHistoryRatesRequestBody {
    private String parseDate;
    private List<String> currencyNames;
}

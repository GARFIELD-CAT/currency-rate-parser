package ru.utmn.currency_rate_parser.model;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyWithRatesDto {
    private Integer currencyId;
    private String currencyName;
    private String currencySymbol;

    private List<CurrencyRateDto> quotes;
}
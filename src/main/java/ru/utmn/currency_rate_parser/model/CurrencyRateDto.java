package ru.utmn.currency_rate_parser.model;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyRateDto {
    private Integer rateId;
    private Double rate;
    private Double change24h;
    private LocalDate currencyRateDate;
    private String baseCurrency;
    private LocalDateTime lastUpdated;
}

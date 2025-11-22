package ru.utmn.currency_rate_parser.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrencyRate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    private Double value;
    private Double change24h;
    private LocalDateTime currencyRateDate;
    private LocalDateTime lastUpdated;

    @ManyToOne()
    @JoinColumn(name = "currency_id")
    private Currency currency;

    public CurrencyRate(Double value, LocalDateTime currencyRateDate, Double change24h, Currency currency) {
        this.value = value;
        this.change24h = change24h;
        this.currencyRateDate = currencyRateDate;
        this.lastUpdated = LocalDateTime.now();
        this.currency = currency;
    }
}

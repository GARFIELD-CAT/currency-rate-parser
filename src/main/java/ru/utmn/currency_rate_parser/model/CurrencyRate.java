package ru.utmn.currency_rate_parser.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(
    name = "currency_rate",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"currency_id", "baseCurrency", "currencyRateDate"}
    ),
    indexes = {
        @Index(name = "currency_rate_date_idx", columnList = "currencyRateDate")
    }
)
public class CurrencyRate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private Double rate;

    @Column(nullable = false)
    private Double change24h;

    @Column(nullable = false)
    private LocalDate currencyRateDate;

    @Column(nullable = false, length = 30)
    private String baseCurrency;

    private LocalDateTime lastUpdated;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    public CurrencyRate(Double rate, Double change24h, LocalDate currencyRateDate, String baseCurrency, Currency currency) {
        this.rate = rate;
        this.change24h = change24h;
        this.currencyRateDate = currencyRateDate;
        this.baseCurrency = baseCurrency;
        this.lastUpdated = LocalDateTime.now();
        this.currency = currency;
    }
}

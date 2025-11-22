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
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"currency_id", "currencyRateDate"}))
public class CurrencyRate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private Double value;

    @Column(nullable = false)
    private Double change24h;

    @Column(nullable = false)
    private LocalDate currencyRateDate;

    private LocalDateTime lastUpdated;

    @ManyToOne()
    @JoinColumn(name = "currency_id", nullable = false)
    private Currency currency;

    public CurrencyRate(Double value, Double change24h, LocalDate currencyRateDate, Currency currency) {
        this.value = value;
        this.change24h = change24h;
        this.currencyRateDate = currencyRateDate;
        this.lastUpdated = LocalDateTime.now();
        this.currency = currency;
    }
}

package ru.utmn.currency_rate_parser.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Currency {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private Integer coinMarketCapId;

    @Column(nullable = false)
    private String currencyName;

    @Column(nullable = false, unique = true)
    private String currencySymbol;

    @OneToMany(mappedBy = "id")
    private List<CurrencyRate> currencyRates;

    public Currency(int coinMarketCapId, String currencyName, String currencySymbol) {
        this.coinMarketCapId = coinMarketCapId;
        this.currencyName = currencyName;
        this.currencySymbol = currencySymbol;
    }
}

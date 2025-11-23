package ru.utmn.currency_rate_parser.repository;

import org.hibernate.query.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.utmn.currency_rate_parser.model.Currency;
import ru.utmn.currency_rate_parser.model.CurrencyRate;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CurrencyRateRepository extends JpaRepository<CurrencyRate, Integer> {
    List<CurrencyRate> findByCurrencyAndCurrencyRateDate(Currency currency, LocalDate currencyRateDate);

    @Query(value = "SELECT currency_rate.id, rate, change24h, currency_rate_date, base_currency, last_updated, currency_id FROM currency_rate\n" +
            "JOIN currency AS c ON c.id = currency_rate.currency_id\n" +
            "WHERE currency_symbol = :currency_symbol AND currency_rate_date = :currency_rate_date", nativeQuery = true)
    List<CurrencyRate> findByCurrencySymbolAndCurrencyRateDate(
            @Param("currency_symbol") String currencySymbol,
            @Param("currency_rate_date") LocalDate currencyRateDate
    );

    @Query(value = "SELECT DISTINCT c.currency_symbol  FROM currency_rate\n" +
            "JOIN currency AS c ON c.id = currency_rate.currency_id", nativeQuery = true)
    List<String> findDistinctCurrencyInfo();
}

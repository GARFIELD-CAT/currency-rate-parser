package ru.utmn.currency_rate_parser.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.utmn.currency_rate_parser.model.Currency;
import ru.utmn.currency_rate_parser.model.CurrencyRate;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CurrencyRateRepository extends JpaRepository<CurrencyRate, Integer> {
    @Query("SELECT cr FROM CurrencyRate cr " +
            "LEFT JOIN FETCH cr.currency c " +
            "WHERE c.id = :currency_id " +
            "AND cr.currencyRateDate = :currency_rate_date")
    List<CurrencyRate> findByCurrencyIdAndCurrencyRateDate(
            @Param("currency_id") Integer currencyId,
            @Param("currency_rate_date") LocalDate currencyRateDate
    );

    Optional<CurrencyRate> findByCurrencyAndCurrencyRateDateAndBaseCurrency(Currency currency, LocalDate currencyRateDate, String baseCurrency);

    @Query(value = "SELECT DISTINCT c.currency_symbol  FROM currency_rate\n" +
            "JOIN currency AS c ON c.id = currency_rate.currency_id " +
            "ORDER BY c.currency_symbol ASC", nativeQuery = true)
    List<String> findDistinctCurrencyInfo();

    @Query("SELECT cr FROM CurrencyRate cr " +
            "LEFT JOIN FETCH cr.currency c " +
            "WHERE c.currencySymbol IN :currency_symbols " +
            "AND cr.currencyRateDate = :currency_rate_date")
    List<CurrencyRate> findByCurrencySymbolsAndCurrencyRateDate(
            @Param("currency_symbols") List<String> currencySymbols,
            @Param("currency_rate_date") LocalDate currencyRateDate
    );
}

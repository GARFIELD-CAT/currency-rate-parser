package ru.utmn.currency_rate_parser.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.utmn.currency_rate_parser.model.Currency;
import ru.utmn.currency_rate_parser.model.CurrencyRate;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface CurrencyRateRepository extends JpaRepository<CurrencyRate, Integer> {
    Optional<CurrencyRate> findByCurrencyAndCurrencyRateDate(Currency currency, LocalDate currencyRateDate);
}

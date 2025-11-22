package ru.utmn.currency_rate_parser.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.utmn.currency_rate_parser.model.Currency;

import java.util.Optional;

@Repository
public interface CurrencyRepository extends JpaRepository<Currency, Integer>{
    Optional<Currency> findByCurrencySymbol(String currencySymbol);
}

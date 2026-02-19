package ru.utmn.currency_rate_parser.service;

import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class CurrencyLockManager {

    // Хранит блокировки: ключ = символ валюты (String), значение = Lock
    private final ConcurrentHashMap<String, Lock> locks = new ConcurrentHashMap<>();

    /**
     * Получает (или создает, если не существует) ReentrantLock для заданной валюты.
     * @param currencySymbol Символ валюты (например, "USD")
     * @return Блокировка, связанная с этой валютой.
     */
    public Lock getLockForCurrency(String currencySymbol) {
        return locks.computeIfAbsent(currencySymbol, key -> new ReentrantLock());
    }
}
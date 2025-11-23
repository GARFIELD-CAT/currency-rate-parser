package ru.utmn.currency_rate_parser.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.utmn.currency_rate_parser.repository.CurrencyRateRepository;
import ru.utmn.currency_rate_parser.repository.CurrencyRepository;


public class LoggerTask implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(LoggerTask.class);

    private final CurrencyRateRepository currencyRateRepository;
    private final CurrencyRepository currencyRepository;
    private final boolean running = true;

    public LoggerTask(CurrencyRateRepository currencyRateRepository, CurrencyRepository currencyRepository) {
        this.currencyRateRepository = currencyRateRepository;
        this.currencyRepository = currencyRepository;
    }

    @Override
    public void run() {
        log.info("{}: Демон логгер запущен.", Thread.currentThread().getName());

        while (running) {
            try {
                long count = currencyRepository.count();
                log.info("{}: Количество валют в базе {}.", Thread.currentThread().getName(), count);

                count = currencyRateRepository.count();
                log.info("{}: Количество курсов валют в базе {}.", Thread.currentThread().getName(), count);

                Thread.sleep(30000);
            } catch (InterruptedException e) {

                log.info("{}: Демон логгер остановлен.", Thread.currentThread().getName());
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.info("{}: Демон логгер, ошибка {}.", Thread.currentThread().getName(), e.getMessage());
                break;
            }
        }

    }
}
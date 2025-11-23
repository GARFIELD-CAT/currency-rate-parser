package ru.utmn.currency_rate_parser.service.aggregator;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static ru.utmn.currency_rate_parser.Constants.*;
import static ru.utmn.currency_rate_parser.utils.CoinMarketCapUrlBuilder.buildListingUrl;

@Component
public class CurrencyRateTaskProducer {
    private static final Logger log = LoggerFactory.getLogger(CurrencyRateTaskProducer.class);

    private final BlockingQueue<CurrencyRateTask> queue = new LinkedBlockingQueue<>(CURRENCY_RATE_TASK_MAX_COUNT);
    private final ScheduledExecutorService scheduler;
    private final ExecutorService producerExecutor = Executors.newFixedThreadPool(PRODUCER_EXECUTOR_COUNT);
    private final int aggregationIntervalMinutes;

    public CurrencyRateTaskProducer() {
        this.aggregationIntervalMinutes = CURRENCY_RATE_AGGREGATION_INTERVAL_MINUTES;
        this.scheduler = Executors.newScheduledThreadPool(1);
    }

    public CurrencyRateTask take() throws InterruptedException {
        return queue.take();
    }

    private void submitCurrencyRateTasks() {
        List<CurrencyRateTask> tasks = generateCurrencyRateTasks();

        int total = tasks.size();
        int chunkSize = (int) Math.ceil((double) total / PRODUCER_EXECUTOR_COUNT);

        for (int i = 0; i < chunkSize; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, total);
            if (start >= total) break;
            List<CurrencyRateTask> chunk = tasks.subList(start, end);
            producerExecutor.submit(() -> processTasks(chunk));
        }
        log.info("Добавлено {} задач в очередь на скачивание курсов валют.", total);
//       TODO: Нужно вынести в Logger
        log.info("Текущий размер очереди задач на скачивание курсов валют {}.", queue.size());
    }

    private void processTasks(List<CurrencyRateTask> chunk) {
        for (var task : chunk) {
            try {
                boolean added = queue.offer(task, 5, TimeUnit.SECONDS);

                if (!added) {
                    log.warn("Очередь заполнена! Потеряна задача с url: {}", task.getUrl());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Генерация задач прервана.", e);
                break;
            }
        }
    }

    private List<CurrencyRateTask> generateCurrencyRateTasks() {
        List<CurrencyRateTask> tasks = new ArrayList<>(TOTAL_CRYPTOCURRENCY_COUNT);
        int pageCount = (int) Math.ceil((double) TOTAL_CRYPTOCURRENCY_COUNT / MAX_CRYPTOCURRENCY_PER_PAGE_COUNT);

        for (int i = 0; i < pageCount; i++) {
            int start = 1;

            if (i != 0) {
                start = start + i * MAX_CRYPTOCURRENCY_PER_PAGE_COUNT;
            }

            tasks.add(new CurrencyRateTask(buildListingUrl(start)));
        }

        return tasks;
    }

    @PostConstruct
    public void start() {
        log.info("Запуск генерации задач на кроулинг курсов валют. Интервал: {} минут.", aggregationIntervalMinutes);
        scheduler.scheduleAtFixedRate(this::submitCurrencyRateTasks, 0, aggregationIntervalMinutes, TimeUnit.MINUTES);
    }

    @PreDestroy
    public void stop() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException ie) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("Генерация задач на кроулинг курсов валют остановлена.");
    }
}

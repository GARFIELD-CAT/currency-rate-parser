package ru.utmn.currency_rate_parser.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public class TimeUtils {
    public static String evaluateExecutionTime(long startTime) {
        long endTime = System.nanoTime();
        long durationNanos = endTime - startTime;
        double durationSeconds = durationNanos / 1_000_000_000.0;

        String message = String.format("Время выполнения: %.3f секунд (%d наносекунд)",
                durationSeconds, durationNanos);
        printExecutionTime(message);

        return message;
    }

    private static void printExecutionTime(String time) {
        System.out.println(time);
    }

    public static long convertDateToTimestamp(LocalDate date) {
        Instant instant = date.atStartOfDay(ZoneId.systemDefault()).toInstant();
        long timestamp = instant.toEpochMilli() / 1000;

        return timestamp;
    }


    public static LocalDate convertStringDateToLocalDate(String dateTimeString) {
        Instant instant = Instant.parse(dateTimeString);
        LocalDate localDate = instant.atZone(ZoneId.of("UTC")).toLocalDate();

        return localDate;
    }
}

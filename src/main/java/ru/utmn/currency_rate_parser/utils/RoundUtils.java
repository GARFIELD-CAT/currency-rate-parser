package ru.utmn.currency_rate_parser.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;


public class RoundUtils {
    public static Double roundDouble(Double value) {
        return BigDecimal.valueOf(value)
                .setScale(10, RoundingMode.HALF_EVEN)
                .doubleValue();
    }
}

package ru.utmn.currency_rate_parser.utils;

import static ru.utmn.currency_rate_parser.Constants.COIN_MARKET_CAP_HISTORICAL_BASE_URL;

public class CoinMarketCapUrlBuilder {
    /**
     * Парсим за конкретный день курс валюты, кроме текущего.
     */
    public static String buildHistoricalUrl(int coinMarketCapId, long timeStart) {
        Integer convertId = 2781;
        long timeEnd = timeStart + 86400;

        return String.format("%s?id=%d&convertId=%d&timeStart=%d&timeEnd=%d&interval=1d",
                COIN_MARKET_CAP_HISTORICAL_BASE_URL, coinMarketCapId, convertId, timeStart, timeEnd);
    }

    /**
     * Парсим за текущий день курс валюты.
     */
    public static String buildHistoricalUrl(int coinMarketCapId) {
        Integer convertId = 2781;

        return String.format("%s?id=%d&convertId=%d&interval=1d",
                COIN_MARKET_CAP_HISTORICAL_BASE_URL, coinMarketCapId, convertId);
    }

}


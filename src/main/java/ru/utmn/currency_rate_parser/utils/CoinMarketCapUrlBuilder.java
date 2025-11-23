package ru.utmn.currency_rate_parser.utils;

import static ru.utmn.currency_rate_parser.Constants.COIN_MARKET_CAP_HISTORICAL_BASE_URL;
import static ru.utmn.currency_rate_parser.Constants.COIN_MARKET_CAP_LISTING_BASE_URL;

public class CoinMarketCapUrlBuilder {
    /**
     * Парсим за конкретный день курс валюты, кроме текущего.
     */
    public static String buildHistoricalUrl(int coinMarketCapId, long timeStart, String convertId) {
        long timeEnd = timeStart + 86400;

        return String.format("%s?id=%d&convertId=%s&timeStart=%d&timeEnd=%d&interval=1d",
                COIN_MARKET_CAP_HISTORICAL_BASE_URL, coinMarketCapId, convertId, timeStart, timeEnd);
    }

    /**
     * Парсим за текущий день курс валюты.
     */
    public static String buildHistoricalUrl(int coinMarketCapId, String convertId) {
        return String.format("%s?id=%d&convertId=%s&interval=1d",
                COIN_MARKET_CAP_HISTORICAL_BASE_URL, coinMarketCapId, convertId);
    }

    /**
     * Парсим актуальную информацию за сутки по всем валютам. Конвертация в USD,RUB.
     */
    public static String buildListingUrl(int start) {
        return String.format("%s?start=%d&limit=100&sortBy=rank&sortType=desc&convert=USD,RUB&cryptoType=all&tagType=all&audited=false&aux=date_added",
                COIN_MARKET_CAP_LISTING_BASE_URL, start);
    }

    /**
     * Парсим актуальную информацию за сутки по всем валютам. Конвертация в выбранной фиатной валюте.
     * Пример параметра convertSymbols = "USD,RUB"
     */
    public static String buildListingUrl(int start, String convertSymbols) {
        return String.format("%s?start=%d&limit=100&sortBy=rank&sortType=desc&convert=%s&cryptoType=all&tagType=all&audited=false&aux=date_added",
                COIN_MARKET_CAP_LISTING_BASE_URL, start, convertSymbols);
    }
}

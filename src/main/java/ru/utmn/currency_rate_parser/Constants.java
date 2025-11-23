package ru.utmn.currency_rate_parser;

public class Constants {
    public final static String COIN_MARKET_CAP_HISTORICAL_BASE_URL = "https://api.coinmarketcap.com/data-api/v3.1/cryptocurrency/historical";
    public final static String COIN_MARKET_CAP_LISTING_BASE_URL = "https://api.coinmarketcap.com/data-api/v3/cryptocurrency/listing";
    public final static String RUB_CONVERT_ID = "2806"; // Рубли (RUB)
    public final static String USD_CONVERT_ID = "2781"; // Доллары (USD)
    public final static Integer FIAT_CURRENCY_COUNT = 2; // Текущее количество фиатных валют, по которым поулчаем курс криптовалют
    public final static Integer CURRENCY_RATE_TASK_MAX_COUNT = 100;
    //    public final static Integer TOTAL_CRYPTOCURRENCY_COUNT = 9130;
    public final static Integer TOTAL_CRYPTOCURRENCY_COUNT = 1001;
    public final static Integer MAX_CRYPTOCURRENCY_PER_PAGE_COUNT = 100;
    public final static Integer PRODUCER_EXECUTOR_COUNT = 10;
    public final static Integer CURRENCY_RATE_AGGREGATION_INTERVAL_MINUTES = 30; // 30 минут.
}
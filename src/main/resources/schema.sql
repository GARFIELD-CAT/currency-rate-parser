-- Создание таблицы Currency
CREATE TABLE currency (
    id INT AUTO_INCREMENT PRIMARY KEY,
    coin_market_cap_id INT NOT NULL UNIQUE,
    currency_name VARCHAR(255) NOT NULL,
    currency_symbol VARCHAR(10) NOT NULL UNIQUE
);

-- Создание таблицы CurrencyRate
CREATE TABLE currency_rate (
    id INT AUTO_INCREMENT PRIMARY KEY,
    rate DOUBLE NOT NULL,
    change24h DOUBLE  NOT NULL,
    currency_rate_date DATE NOT NULL,
    base_currency  VARCHAR(10) NOT NULL,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    currency_id INT NOT NULL,
    FOREIGN KEY (currency_id) REFERENCES Currency(id) ON DELETE CASCADE,
    UNIQUE (currency_id, base_currency, currency_rate_date)
);

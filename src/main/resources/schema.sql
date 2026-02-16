-- Создание таблицы Currency
CREATE TABLE IF NOT EXISTS currency (
    id SERIAL PRIMARY KEY,
    coin_market_cap_id INT NOT NULL UNIQUE,
    currency_name VARCHAR(255) NOT NULL,
    currency_symbol VARCHAR(30) NOT NULL UNIQUE
);

-- Создание таблицы CurrencyRate
CREATE TABLE IF NOT EXISTS currency_rate (
    id SERIAL PRIMARY KEY,
    rate DECIMAL(20, 10) NOT NULL,
    change24h DECIMAL(20, 10) NOT NULL,
    currency_rate_date DATE NOT NULL,
    base_currency VARCHAR(30) NOT NULL,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    currency_id INT NOT NULL,
    FOREIGN KEY (currency_id) REFERENCES currency(id) ON DELETE CASCADE,
    UNIQUE (currency_id, base_currency, currency_rate_date)
);

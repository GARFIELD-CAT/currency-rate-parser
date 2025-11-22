-- Создание таблицы Currency
CREATE TABLE Currency (
    id INT AUTO_INCREMENT PRIMARY KEY,
    coinMarketCapId INT NOT NULL UNIQUE,
    currencyName VARCHAR(255) NOT NULL,
    currencySymbol VARCHAR(10) NOT NULL UNIQUE
);

-- Создание таблицы CurrencyRate
CREATE TABLE CurrencyRate (
    id INT AUTO_INCREMENT PRIMARY KEY,
    "value" DOUBLE  NOT NULL,
    change24h DOUBLE  NOT NULL,
    currencyRateDate DATE NOT NULL,
    lastUpdated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    currency_id INT NOT NULL,
    FOREIGN KEY (currency_id) REFERENCES Currency(id) ON DELETE CASCADE,
    UNIQUE (currency_id, currencyRateDate)
);

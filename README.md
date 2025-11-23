# Currency Rate Parser API

Этот проект предоставляет API для получения актуальных курсов криптовалют, скачивания исторических курсов и получения списка доступных криптовалют.

## Запуск проекта

1. **Клонируйте репозиторий**:
   ```bash
   git clone https://github.com/GARFIELD-CAT/currency-rate-parser.git
   ```

2. **Перейдите в каталог проекта**:
   ```bash
   cd currency-rate-parser
   ```

3. **Запустите проект**:
   Убедитесь, что у вас установлен [Maven](https://maven.apache.org/) Запустите проект командой:
   ```bash
   mvn spring-boot:run
   ```

4. **Сервис будет доступен по URL**:
   ```
    http://localhost:8080/swagger-ui/index.html
   ```

## Использование API

### 1. Получить актуальные курсы валют за сегодня

- **URL**: `http://localhost:8080/api/v1/currency-rates/get-all`
- **Метод**: `GET`
- **Параметры**:
    - `page` (по умолчанию 0): Номер страницы.
    - `size` (по умолчанию 100): Количество записей на странице.

**Пример запроса**:
```http
GET http://localhost:8080/api/v1/currency-rates/get-all?page=0&size=100
```

### 2. Скачать курсы валют за определенный день

- **URL**: `http://localhost:8080/api/v1/currency-rates/parse`
- **Метод**: `POST`
- **Тело запроса**:
```json
{
    "manualParse": false,
    "parseDate": "2025-11-22",
    "currencySymbols": ["BTC", "ETH", "USDT"]
}
```

**Пример запроса**:
```http
POST http://localhost:8080/api/v1/currency-rates/parse
Content-Type: application/json

{
    "manualParse": false,
    "parseDate": "2025-11-22",
    "currencySymbols": ["BTC", "ETH", "USDT"]
}
```

### 3. Получить список всех криптовалют, доступных для скачивания

- **URL**: `http://localhost:8080/api/v1/currency-rates/get-all-currency`
- **Метод**: `POST`
- **Параметры**:
    - `page` (по умолчанию 0): Номер страницы.
    - `size` (по умолчанию 100): Количество записей на странице.
    - `sortBy` (по умолчанию `coinMarketCapId`): Поле для сортировки.
    - `sortDir` (по умолчанию `asc`): Направление сортировки (`asc` или `desc`).

**Пример запроса**:
```http
GET http://localhost:8080/api/v1/currency-rates/get-all-currency?page=0&size=10&sortBy=coinMarketCapId&sortDir=asc
```

## Технологии

- Spring Boot
- Maven
- Swagger для документации

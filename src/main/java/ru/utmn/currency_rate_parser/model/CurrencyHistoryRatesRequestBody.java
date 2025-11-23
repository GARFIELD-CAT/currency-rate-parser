package ru.utmn.currency_rate_parser.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CurrencyHistoryRatesRequestBody {
    @Schema(description = "Запустить ли обнровление данных вручную за выбранны день", example = "False")
    private Boolean manualParse;
    @Schema(description = "Запрашиваемая дата в формате YYYY-MM-DD", example = "2025-11-22")
    private String parseDate;
    @Schema(description = "Список названий криптовалют, например: [\"BTC\", \"ETH\", \"USDD\"]", example = "[\"BTC\", \"ETH\", \"USDD\"]")
    private List<String> currencyNames;
}

package ru.utmn.currency_rate_parser.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

@Getter
@Setter
public class CurrencyHistoryRatesRequestBody {
    @Schema(requiredMode = REQUIRED, description = "Запустить ли обновление данных вручную за выбранный день", example = "False")
    private Boolean manualParse;

    @Schema(requiredMode = REQUIRED, description = "Запрашиваемая дата в формате YYYY-MM-DD", example = "2025-11-22")
    private String parseDate;

    @Size(max = 100)
    @Schema(requiredMode = REQUIRED, description = "Список названий криптовалют, например: [\"BTC\", \"ETH\", \"USDD\"]", example = "[\"BTC\", \"ETH\", \"USDD\"]")
    private List<String> currencySymbols;
}

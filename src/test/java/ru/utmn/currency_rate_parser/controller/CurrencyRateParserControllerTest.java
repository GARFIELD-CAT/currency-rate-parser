package ru.utmn.currency_rate_parser.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import ru.utmn.currency_rate_parser.model.*;
import ru.utmn.currency_rate_parser.service.CurrencyRateParserService;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CurrencyRateParserController.class)
@AutoConfigureMockMvc
public class CurrencyRateParserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrencyRateParserService currencyRateParserService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAllCurrencyRates_shouldReturnListOfCurrencyWithRatesDto() throws Exception {
        int page = 0;
        int size = 100;
        List<CurrencyWithRatesDto> expectedRates = new ArrayList<>();
        CurrencyWithRatesDto dto1 = new CurrencyWithRatesDto(1, "Bitcoin", "BTC", new ArrayList<>());
        CurrencyWithRatesDto dto2 = new CurrencyWithRatesDto(2, "Ethereum", "ETH", new ArrayList<>());
        expectedRates.add(dto1);
        expectedRates.add(dto2);

        Mockito.when(currencyRateParserService.findAllCurrencyWithRates(page, size))
                .thenReturn(expectedRates);

        mockMvc.perform(get("/api/v1/currency-rates/get-all")
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].currencyId").value(1))
                .andExpect(jsonPath("$[0].currencyName").value("Bitcoin"))
                .andExpect(jsonPath("$[1].currencyId").value(2))
                .andExpect(jsonPath("$[1].currencyName").value("Ethereum"));
    }

    @Test
    void getAllCurrencyRates_withDefaultParams_shouldReturnListOfCurrencyWithRatesDto() throws Exception {
        List<CurrencyWithRatesDto> expectedRates = new ArrayList<>();
        CurrencyWithRatesDto dto1 = new CurrencyWithRatesDto(1, "Bitcoin", "BTC", new ArrayList<>());
        expectedRates.add(dto1);

        Mockito.when(currencyRateParserService.findAllCurrencyWithRates(0, 100))
                .thenReturn(expectedRates);

        mockMvc.perform(get("/api/v1/currency-rates/get-all")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].currencyId").value(1));
    }

    @Test
    void getAllCurrencyRates_whenServiceReturnsEmptyList_shouldReturnEmptyList() throws Exception {
        Mockito.when(currencyRateParserService.findAllCurrencyWithRates(anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/currency-rates/get-all")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void parseCurrencyRates_shouldReturnListOfCurrencyRate() throws Exception {
        // Given
        CurrencyHistoryRatesRequestBody requestBody = new CurrencyHistoryRatesRequestBody();
        requestBody.setParseDate("2023-10-27");
        requestBody.setManualParse(true);
        requestBody.setCurrencySymbols(Arrays.asList("BTC", "ETH", "BTC"));

        LocalDate parsedDate = LocalDate.parse("2023-10-27");
        List<String> uniqueSymbols = Arrays.asList("BTC", "ETH");

        List<CurrencyRate> expectedRates = new ArrayList<>();
        Currency btcCurrency = new Currency(1, 100, "Bitcoin", "BTC");
        CurrencyRate rate1 = new CurrencyRate(1000.0, 50.0, parsedDate, "USD", btcCurrency);
        CurrencyRate rate2 = new CurrencyRate(2000.0, 100.0, parsedDate, "USD", new Currency(2, 200, "Ethereum", "ETH"));
        expectedRates.add(rate1);
        expectedRates.add(rate2);

        Mockito.when(currencyRateParserService.parseCurrencyRates(eq(parsedDate), Mockito.eq(uniqueSymbols), eq(true)))
                .thenReturn(expectedRates);

        mockMvc.perform(post("/api/v1/currency-rates/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].rate").value(1000.0))
                .andExpect(jsonPath("$[0].currencyRateDate").value("2023-10-27"))
                .andExpect(jsonPath("$[0].baseCurrency").value("USD"))
                .andExpect(jsonPath("$[1].rate").value(2000.0))
                .andExpect(jsonPath("$[1].currencyRateDate").value("2023-10-27"))
                .andExpect(jsonPath("$[1].baseCurrency").value("USD"));
    }

    @Test
    void parseCurrencyRates_withNullSymbol_shouldFilterNulls() throws Exception {
        CurrencyHistoryRatesRequestBody requestBody = new CurrencyHistoryRatesRequestBody();
        requestBody.setParseDate("2023-10-27");
        requestBody.setManualParse(false);
        requestBody.setCurrencySymbols(Arrays.asList("BTC", null, "ETH"));

        LocalDate parsedDate = LocalDate.parse("2023-10-27");
        List<String> uniqueSymbols = Arrays.asList("BTC", "ETH");

        List<CurrencyRate> expectedRates = new ArrayList<>();
        Currency btcCurrency = new Currency(1, 100, "Bitcoin", "BTC");
        CurrencyRate rate1 = new CurrencyRate(1000.0, 50.0, parsedDate, "USD", btcCurrency);
        expectedRates.add(rate1);

        Mockito.when(currencyRateParserService.parseCurrencyRates(eq(parsedDate), Mockito.eq(uniqueSymbols), eq(false)))
                .thenReturn(expectedRates);

        mockMvc.perform(post("/api/v1/currency-rates/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].rate").value(1000.0));
    }

    @Test
    void parseCurrencyRates_invalidDate_shouldThrowException() throws Exception {
        // Given
        CurrencyHistoryRatesRequestBody requestBody = new CurrencyHistoryRatesRequestBody();
        requestBody.setParseDate("2023/10/27"); // Неправильный формат
        requestBody.setManualParse(true);
        requestBody.setCurrencySymbols(Arrays.asList("BTC"));

        // When & Then
        mockMvc.perform(post("/api/v1/currency-rates/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest()); // Ожидаем 400 Bad Request из-за некорректной даты
    }

    @Test
    void getAllCurrency_shouldReturnListOfCurrency() throws Exception {
        // Given
        int page = 0;
        int size = 50;
        String sortBy = "currencyName";
        String sortDir = "asc";

        List<Currency> expectedCurrencies = new ArrayList<>();
        Currency currency1 = new Currency(1, 100, "Bitcoin", "BTC");
        Currency currency2 = new Currency(2, 200, "Ethereum", "ETH");
        expectedCurrencies.add(currency1);
        expectedCurrencies.add(currency2);

        Page<Currency> currencyPage = Mockito.mock(Page.class);
        Mockito.when(currencyPage.getContent()).thenReturn(expectedCurrencies);

        Sort sort = Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Mockito.when(currencyRateParserService.getAllCurrency(pageable))
                .thenReturn(currencyPage);

        mockMvc.perform(get("/api/v1/currency-rates/get-all-currency")
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size))
                        .param("sortBy", sortBy)
                        .param("sortDir", sortDir)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].currencyName").value("Bitcoin"))
                .andExpect(jsonPath("$[0].currencySymbol").value("BTC"))
                .andExpect(jsonPath("$[1].id").value(2))
                .andExpect(jsonPath("$[1].currencyName").value("Ethereum"));
    }

    @Test
    void getAllCurrency_withDefaultParams_shouldReturnListOfCurrency() throws Exception {
        List<Currency> expectedCurrencies = new ArrayList<>();
        Currency currency1 = new Currency(1, 100, "Bitcoin", "BTC");
        expectedCurrencies.add(currency1);

        Page<Currency> currencyPage = Mockito.mock(Page.class);
        Mockito.when(currencyPage.getContent()).thenReturn(expectedCurrencies);

        Mockito.when(currencyRateParserService.getAllCurrency(PageRequest.of(0, 100, Sort.by("coinMarketCapId").ascending())))
                .thenReturn(currencyPage);

        mockMvc.perform(get("/api/v1/currency-rates/get-all-currency")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void getAllCurrency_withSortDirectionDescending_shouldApplyCorrectSort() throws Exception {
        String sortBy = "currencyName";
        String sortDir = "desc";

        List<Currency> expectedCurrencies = new ArrayList<>();
        Currency currency1 = new Currency(2, 200, "Ethereum", "ETH");
        Currency currency2 = new Currency(1, 100, "Bitcoin", "BTC");
        expectedCurrencies.add(currency1);
        expectedCurrencies.add(currency2);

        Page<Currency> currencyPage = Mockito.mock(Page.class);
        Mockito.when(currencyPage.getContent()).thenReturn(expectedCurrencies);

        Sort sort = Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(0, 100, sort);

        Mockito.when(currencyRateParserService.getAllCurrency(pageable))
                .thenReturn(currencyPage);

        mockMvc.perform(get("/api/v1/currency-rates/get-all-currency")
                        .param("sortBy", sortBy)
                        .param("sortDir", sortDir)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].currencyName").value("Ethereum"))
                .andExpect(jsonPath("$[1].currencyName").value("Bitcoin"));
    }

    @Test
    void getAllCurrency_whenServiceReturnsEmptyList_shouldReturnEmptyList() throws Exception {
        Page<Currency> currencyPage = Mockito.mock(Page.class);
        Mockito.when(currencyPage.getContent()).thenReturn(Collections.emptyList());

        Mockito.when(currencyRateParserService.getAllCurrency(any(Pageable.class)))
                .thenReturn(currencyPage);

        mockMvc.perform(get("/api/v1/currency-rates/get-all-currency")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isEmpty());
    }
}
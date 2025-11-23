package ru.utmn.currency_rate_parser.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ListingApiResponse {
    private Data data;
    private Status status;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private List<CryptoCurrency> cryptoCurrencyList;
        private String totalCount;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CryptoCurrency {
        private int id;
        private String name;
        private String symbol;

        private List<Quote> quotes;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Quote {
        private String name;
        private double price;
        private double percentChange24h;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Status {
        @JsonProperty("error_code")
        private String errorCode;
        @JsonProperty("error_message")
        private String errorMessage;
    }
}

package ru.utmn.currency_rate_parser.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class HistoryApiResponse {
    private Data data;
    private Status status;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private int id;
        private String name;
        private String symbol;
        private String timeEnd;
        private List<QuoteData> quotes;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class QuoteData {
        private Quote quote;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Quote {
        private String name;
        private double open;
        private double high;
        private double low;
        private double close;
        private double volume;
        private double marketCap;
        private double circulatingSupply;
        private String timestamp;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Status {
        @JsonProperty("error_code")
        private String errorCode;
        @JsonProperty("error_message")
        private String errorMessage;
    }
}

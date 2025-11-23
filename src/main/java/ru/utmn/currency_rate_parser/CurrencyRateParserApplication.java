package ru.utmn.currency_rate_parser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import ru.utmn.currency_rate_parser.service.aggregator.CurrencyRateTaskProducer;

@SpringBootApplication
public class CurrencyRateParserApplication {
    private static CurrencyRateTaskProducer currencyRateTaskProducer;

    public CurrencyRateParserApplication(CurrencyRateTaskProducer currencyRateTaskProducer) {
        CurrencyRateParserApplication.currencyRateTaskProducer = currencyRateTaskProducer;
    }

    public static void main(String[] args) {
		SpringApplication.run(CurrencyRateParserApplication.class, args);
        currencyRateTaskProducer.start();
        System.out.println("Ok");
	}
}

package ru.utmn.currency_rate_parser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CurrencyRateParserApplication {
    public static void main(String[] args) {
		SpringApplication.run(CurrencyRateParserApplication.class, args);
        System.out.println("Ok");
	}
}

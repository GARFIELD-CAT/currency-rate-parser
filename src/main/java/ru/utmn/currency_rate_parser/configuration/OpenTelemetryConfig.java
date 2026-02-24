package ru.utmn.currency_rate_parser.configuration;


import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import lombok.extern.slf4j.Slf4j;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static ru.utmn.currency_rate_parser.Constants.SERVICE_NAME;
import static ru.utmn.currency_rate_parser.Constants.SERVICE_VERSION;


@Configuration
@Slf4j
public class OpenTelemetryConfig {

    @Bean
    public OpenTelemetry openTelemetry() {
        log.info("Open telemetry sdk inizialize");

        Resource resource = Resource.builder()
                .put(ResourceAttributes.SERVICE_NAME, SERVICE_NAME)
                .put(ResourceAttributes.SERVICE_VERSION, SERVICE_VERSION)
                .build();

        OtlpHttpSpanExporter spanExporter = OtlpHttpSpanExporter.builder()
                .setEndpoint("http://localhost:4318/v1/traces")
                .setTimeout(Duration.ofSeconds(10))
                .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setPropagators(ContextPropagators.create(W3CTraceContextPropagator.getInstance()))
                .build();
    }

    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(SERVICE_NAME, SERVICE_VERSION);
    }
}
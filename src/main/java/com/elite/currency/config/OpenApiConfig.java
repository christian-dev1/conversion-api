package com.elite.currency.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private int port;

    @Bean
    public OpenAPI currencyConversionOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Currency Conversion API")
                        .description("""
                            ## Overview
                            Real-time currency conversion using **ExchangeRate-API v6**.

                            ### Features
                            - 150+ ISO 4217 currencies supported
                            - Exchange rates cached for **1 hour** (Caffeine)
                            - **Circuit Breaker** + **Retry** via Resilience4j
                            - Full **OpenAPI 3** documentation
                            - Spring Actuator health/metrics endpoints

                            ### Quick Test
                            ```
                            curl -X POST http://localhost:8080/api/v1/convert \\
                              -H "Content-Type: application/json" \\
                              -d '{"from":"USD","to":"EUR","amount":1000}'
                            ```

                            ### Rate Limiting
                            100 requests per minute per client IP.
                            """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Elite Engineering")
                                .email("dev@elite.com")
                                .url("https://github.com/elite/currency-conversion-api"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:" + port).description("Local Development"),
                        new Server().url("https://api.elite.com").description("Production")));
    }
}

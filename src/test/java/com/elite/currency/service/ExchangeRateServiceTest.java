package com.elite.currency.service;

import com.elite.currency.dto.ConversionRequest;
import com.elite.currency.dto.ConversionResponse;
import com.elite.currency.exception.ExternalApiException;
import com.elite.currency.exception.InvalidCurrencyException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.*;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ExchangeRateService — Unit Tests")
class ExchangeRateServiceTest {

    private static MockWebServer mockServer;
    private ExchangeRateService service;

    @BeforeAll
    static void startServer() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
    }

    @AfterAll
    static void stopServer() throws IOException {
        mockServer.shutdown();
    }

    @BeforeEach
    void setUp() {
        String baseUrl = "http://localhost:" + mockServer.getPort();
        service = new ExchangeRateService(WebClient.builder(), baseUrl, "test-key");
    }

    @Test
    @DisplayName("Should convert USD to EUR correctly")
    void convert_usdToEur_success() {
        mockServer.enqueue(new MockResponse()
                .setBody("""
                    {
                      "result": "success",
                      "base_code": "USD",
                      "time_last_update_unix": 1710460801,
                      "conversion_rates": {
                        "USD": 1.0,
                        "EUR": 0.9213,
                        "XAF": 614.56
                      }
                    }
                    """)
                .addHeader("Content-Type", "application/json"));

        ConversionRequest request = ConversionRequest.builder()
                .from("USD").to("EUR").amount(1000.0).build();

        ConversionResponse response = service.convert(request);

        assertThat(response).isNotNull();
        assertThat(response.getFrom()).isEqualTo("USD");
        assertThat(response.getTo()).isEqualTo("EUR");
        assertThat(response.getAmount()).isEqualTo(1000.0);
        assertThat(response.getConvertedAmount()).isEqualTo(921.3);
        assertThat(response.getExchangeRate()).isEqualTo(0.9213);
        assertThat(response.getInverseRate()).isPositive();
        assertThat(response.getResponseTimestamp()).isNotNull();
        assertThat(response.getRateLastUpdated()).isNotNull();
    }

    @Test
    @DisplayName("Should convert USD to XAF and round to 4 decimal places")
    void convert_usdToXaf_roundsCorrectly() {
        mockServer.enqueue(new MockResponse()
                .setBody("""
                    {
                      "result": "success",
                      "base_code": "USD",
                      "time_last_update_unix": 1710460801,
                      "conversion_rates": { "USD": 1.0, "XAF": 614.5678 }
                    }
                    """)
                .addHeader("Content-Type", "application/json"));

        ConversionResponse response = service.convert(
                ConversionRequest.builder().from("USD").to("XAF").amount(100.0).build());

        assertThat(response.getConvertedAmount()).isEqualTo(61456.78);
    }

    @Test
    @DisplayName("Should throw InvalidCurrencyException for unknown target currency")
    void convert_unknownTarget_throwsInvalidCurrencyException() {
        mockServer.enqueue(new MockResponse()
                .setBody("""
                    {
                      "result": "success",
                      "base_code": "USD",
                      "time_last_update_unix": 1710460801,
                      "conversion_rates": { "USD": 1.0, "EUR": 0.9213 }
                    }
                    """)
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() ->
                service.convert(ConversionRequest.builder().from("USD").to("XYZ").amount(100.0).build()))
                .isInstanceOf(InvalidCurrencyException.class)
                .hasMessageContaining("XYZ");
    }

    @Test
    @DisplayName("Should throw InvalidCurrencyException for unsupported base currency")
    void convert_unsupportedBase_throwsInvalidCurrencyException() {
        mockServer.enqueue(new MockResponse()
                .setBody("""
                    {
                      "result": "error",
                      "error-type": "unsupported-code"
                    }
                    """)
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() ->
                service.convert(ConversionRequest.builder().from("XYZ").to("EUR").amount(100.0).build()))
                .isInstanceOf(InvalidCurrencyException.class);
    }

    @Test
    @DisplayName("Should throw ExternalApiException when provider returns 500")
    void convert_providerError_throwsExternalApiException() {
        mockServer.enqueue(new MockResponse().setResponseCode(500));

        assertThatThrownBy(() ->
                service.convert(ConversionRequest.builder().from("USD").to("EUR").amount(100.0).build()))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    @DisplayName("Inverse rate should be 1 / exchange rate")
    void convert_inverseRate_isCorrect() {
        mockServer.enqueue(new MockResponse()
                .setBody("""
                    {
                      "result": "success",
                      "base_code": "USD",
                      "time_last_update_unix": 1710460801,
                      "conversion_rates": { "USD": 1.0, "EUR": 0.9213 }
                    }
                    """)
                .addHeader("Content-Type", "application/json"));

        ConversionResponse response = service.convert(
                ConversionRequest.builder().from("USD").to("EUR").amount(1.0).build());

        double expected = Math.round((1.0 / 0.9213) * 10_000d) / 10_000d;
        assertThat(response.getInverseRate()).isEqualTo(expected);
    }
}

package com.elite.currency.controller;

import com.elite.currency.dto.ConversionRequest;
import com.elite.currency.dto.ConversionResponse;
import com.elite.currency.exception.ExternalApiException;
import com.elite.currency.exception.InvalidCurrencyException;
import com.elite.currency.service.ExchangeRateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CurrencyConversionController.class)
@DisplayName("CurrencyConversionController — Integration Tests")
class CurrencyConversionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean ExchangeRateService exchangeRateService;

    private ConversionResponse sampleResponse() {
        return ConversionResponse.builder()
                .from("USD").to("EUR")
                .amount(1000.0).convertedAmount(921.3)
                .exchangeRate(0.9213).inverseRate(1.0854)
                .cached(false)
                .rateLastUpdated(Instant.parse("2024-03-15T00:00:01Z"))
                .responseTimestamp(Instant.now())
                .build();
    }

    @Test
    @DisplayName("POST /convert — valid request returns 200 with converted amount")
    void postConvert_validRequest_returns200() throws Exception {
        when(exchangeRateService.convert(any())).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "from": "USD", "to": "EUR", "amount": 1000.00 }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("USD"))
                .andExpect(jsonPath("$.to").value("EUR"))
                .andExpect(jsonPath("$.convertedAmount").value(921.3))
                .andExpect(jsonPath("$.exchangeRate").value(0.9213));
    }

    @Test
    @DisplayName("POST /convert — missing 'from' returns 400")
    void postConvert_missingFrom_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "to": "EUR", "amount": 100.0 }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.violations").isArray());
    }

    @Test
    @DisplayName("POST /convert — negative amount returns 400")
    void postConvert_negativeAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "from": "USD", "to": "EUR", "amount": -50.0 }
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /convert — invalid currency code pattern returns 400")
    void postConvert_invalidCurrencyPattern_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "from": "us", "to": "EUR", "amount": 100.0 }
                            """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /convert — unknown currency returns 422")
    void postConvert_unknownCurrency_returns422() throws Exception {
        when(exchangeRateService.convert(any())).thenThrow(new InvalidCurrencyException("XYZ"));

        mockMvc.perform(post("/api/v1/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "from": "USD", "to": "XYZ", "amount": 100.0 }
                            """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INVALID_CURRENCY"));
    }

    @Test
    @DisplayName("POST /convert — provider down returns 503")
    void postConvert_providerDown_returns503() throws Exception {
        when(exchangeRateService.convert(any())).thenThrow(new ExternalApiException("provider unavailable"));

        mockMvc.perform(post("/api/v1/convert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            { "from": "USD", "to": "EUR", "amount": 100.0 }
                            """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("EXTERNAL_API_UNAVAILABLE"));
    }

    @Test
    @DisplayName("GET /convert — valid query params returns 200")
    void getConvert_validParams_returns200() throws Exception {
        when(exchangeRateService.convert(any())).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/convert")
                        .param("from", "USD")
                        .param("to", "EUR")
                        .param("amount", "1000.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convertedAmount").value(921.3));
    }
}

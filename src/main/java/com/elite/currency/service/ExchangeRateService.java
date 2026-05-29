package com.elite.currency.service;

import com.elite.currency.dto.ConversionRequest;
import com.elite.currency.dto.ConversionResponse;
import com.elite.currency.exception.ExternalApiException;
import com.elite.currency.exception.InvalidCurrencyException;
import com.elite.currency.model.ExchangeRateApiResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ExchangeRateService {

    private final WebClient webClient;
    private final String apiKey;

    private final Map<String, Boolean> cacheHitTracker = new ConcurrentHashMap<>();

    public ExchangeRateService(
            WebClient.Builder webClientBuilder,
            @Value("${exchange-rate.api.base-url}") String baseUrl,
            @Value("${exchange-rate.api.api-key}") String apiKey) {

        this.webClient = webClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    public ConversionResponse convert(ConversionRequest request) {
        String from = request.getFrom().toUpperCase();
        String to   = request.getTo().toUpperCase();

        log.info("Conversion requested: {} {} → {}", request.getAmount(), from, to);

        boolean wasAlreadyCached = cacheHitTracker.containsKey(from);
        ExchangeRateApiResponse rateResponse = fetchRates(from);

        Map<String, Double> rates = rateResponse.getConversionRates();

        if (!rates.containsKey(to)) {
            throw new InvalidCurrencyException(to);
        }

        double rate          = rates.get(to);
        double converted     = Math.round(request.getAmount() * rate * 10_000d) / 10_000d;
        double inverseRate   = rate != 0 ? Math.round((1d / rate) * 10_000d) / 10_000d : 0;

        cacheHitTracker.put(from, true);

        return ConversionResponse.builder()
                .from(from)
                .to(to)
                .amount(request.getAmount())
                .convertedAmount(converted)
                .exchangeRate(rate)
                .inverseRate(inverseRate)
                .cached(wasAlreadyCached)
                .rateLastUpdated(rateResponse.getRateLastUpdated())
                .responseTimestamp(Instant.now())
                .build();
    }

    public Map<String, Double> getSupportedCurrencies(String baseCurrency) {
        ExchangeRateApiResponse response = fetchRates(baseCurrency.toUpperCase());
        return response.getConversionRates();
    }

    @Cacheable(value = "exchange-rates", key = "#baseCurrency")
    @CircuitBreaker(name = "exchangeRateService", fallbackMethod = "fetchRatesFallback")
    @Retry(name = "exchangeRateService")
    public ExchangeRateApiResponse fetchRates(String baseCurrency) {
        log.debug("Fetching live rates for base currency: {}", baseCurrency);

        ExchangeRateApiResponse response = webClient.get()
                .uri("/{apiKey}/latest/{base}", apiKey, baseCurrency)
                .retrieve()
                .bodyToMono(ExchangeRateApiResponse.class)
                .block();

        if (response == null || !response.isSuccess()) {
            String errorType = response != null ? response.getErrorType() : "null-response";
            handleProviderError(baseCurrency, errorType);
        }

        log.debug("Received {} rates for {}", response.getConversionRates().size(), baseCurrency);
        return response;
    }

    @SuppressWarnings("unused")
    public ExchangeRateApiResponse fetchRatesFallback(String baseCurrency, Throwable t) {
        log.error("Circuit breaker fallback triggered for {}: {}", baseCurrency, t.getMessage());
        throw new ExternalApiException(
                "Exchange-rate service unavailable for base currency '" + baseCurrency + "'.", t);
    }


    @Scheduled(fixedRate = 3_600_000, initialDelay = 3_600_000)
    @CacheEvict(value = "exchange-rates", allEntries = true)
    public void evictRateCache() {
        log.info("Scheduled cache eviction: all exchange-rate entries cleared");
        cacheHitTracker.clear();
    }

    private void handleProviderError(String baseCurrency, String errorType) {
        switch (String.valueOf(errorType)) {
            case "unsupported-code" -> throw new InvalidCurrencyException(baseCurrency);
            case "invalid-key"      -> throw new ExternalApiException(
                    "Invalid API key configured. Contact the system administrator.");
            case "quota-reached"    -> throw new ExternalApiException(
                    "Exchange-rate API monthly quota reached. Please upgrade the plan.");
            default                 -> throw new ExternalApiException(
                    "Exchange-rate provider returned an error: " + errorType);
        }
    }
}

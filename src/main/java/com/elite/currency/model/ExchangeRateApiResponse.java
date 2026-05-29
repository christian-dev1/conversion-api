package com.elite.currency.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.Instant;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExchangeRateApiResponse {

    @JsonProperty("result")
    private String result;

    @JsonProperty("base_code")
    private String baseCode;

    @JsonProperty("conversion_rates")
    private Map<String, Double> conversionRates;

    @JsonProperty("time_last_update_unix")
    private Long timeLastUpdateUnix;

    @JsonProperty("error-type")
    private String errorType;

    public boolean isSuccess() {
        return "success".equalsIgnoreCase(result);
    }

    public Instant getRateLastUpdated() {
        return timeLastUpdateUnix != null ? Instant.ofEpochSecond(timeLastUpdateUnix) : null;
    }
}

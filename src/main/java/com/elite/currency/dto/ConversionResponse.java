package com.elite.currency.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ConversionResponse", description = "Currency conversion result")
public class ConversionResponse {

    @Schema(description = "ISO 4217 source currency code", example = "USD")
    private String from;

    @Schema(description = "ISO 4217 target currency code", example = "EUR")
    private String to;

    @Schema(description = "Original amount provided by the caller", example = "1000.00")
    private Double amount;

    @Schema(description = "Converted amount rounded to 4 decimal places", example = "921.3000")
    private Double convertedAmount;

    @Schema(description = "Exchange rate used (from → to)", example = "0.9213")
    private Double exchangeRate;

    @Schema(description = "Inverse rate (to → from)", example = "1.0854")
    private Double inverseRate;

    @Schema(description = "Indicates whether the rate was served from cache", example = "true")
    private boolean cached;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(description = "UTC timestamp of the rate's last update on the external provider", example = "2024-03-15T10:00:00Z")
    private Instant rateLastUpdated;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(description = "UTC timestamp of this API response", example = "2024-03-15T10:05:30Z")
    private Instant responseTimestamp;
}

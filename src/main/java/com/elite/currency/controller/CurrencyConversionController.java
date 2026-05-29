package com.elite.currency.controller;

import com.elite.currency.dto.ConversionRequest;
import com.elite.currency.dto.ConversionResponse;
import com.elite.currency.dto.ErrorResponse;
import com.elite.currency.service.ExchangeRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Currency Conversion", description = "Real-time currency conversion powered by ExchangeRate-API v6")
public class CurrencyConversionController {

    private final ExchangeRateService exchangeRateService;

    @Operation(
        summary     = "Convert an amount between two currencies",
        description = """
            Accepts a source currency, target currency, and amount.
            Returns the converted amount together with the exchange rate, inverse rate,
            cache status, and provider timestamp.

            **Rate caching:** Exchange rates are cached for 1 hour per base currency to
            minimise provider API calls. The `cached` field in the response indicates
            whether the rate was served from cache.

            **Resilience:** The endpoint is protected by a Resilience4j circuit breaker.
            If the external provider is unreachable after 3 retries, a `503` is returned.
            """
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Conversion successful",
            content = @Content(schema = @Schema(implementation = ConversionResponse.class),
                examples = @ExampleObject(name = "USD → EUR", value = """
                    {
                      "from": "USD",
                      "to": "EUR",
                      "amount": 1000.0,
                      "convertedAmount": 921.3,
                      "exchangeRate": 0.9213,
                      "inverseRate": 1.0854,
                      "cached": false,
                      "rateLastUpdated": "2024-03-15T00:00:01Z",
                      "responseTimestamp": "2024-03-15T10:05:30Z"
                    }
                    """))),
        @ApiResponse(responseCode = "400", description = "Request payload failed validation",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Currency code not supported by the provider",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "External exchange-rate service unavailable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/convert")
    public ResponseEntity<ConversionResponse> convert(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Conversion parameters",
                required = true,
                content = @Content(examples = {
                    @ExampleObject(name = "USD to EUR", value = """
                        { "from": "USD", "to": "EUR", "amount": 1000.00 }"""),
                    @ExampleObject(name = "USD to XAF (CFA Franc)", value = """
                        { "from": "USD", "to": "XAF", "amount": 500.00 }"""),
                    @ExampleObject(name = "GBP to JPY", value = """
                        { "from": "GBP", "to": "JPY", "amount": 250.00 }""")
                })
            )
            @Valid @RequestBody ConversionRequest request) {

        ConversionResponse response = exchangeRateService.convert(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary     = "Convert via query parameters (GET)",
        description = "Convenience endpoint — same logic as POST /convert but accepts query params. "
                    + "Useful for quick browser/curl testing."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Conversion successful",
            content = @Content(schema = @Schema(implementation = ConversionResponse.class))),
        @ApiResponse(responseCode = "400", description = "Validation error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Unsupported currency code",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "External API unavailable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/convert")
    public ResponseEntity<ConversionResponse> convertGet(
            @Parameter(description = "ISO 4217 source currency", example = "USD", required = true)
            @RequestParam @Pattern(regexp = "^[A-Z]{3}$", message = "Must be 3 uppercase letters") String from,

            @Parameter(description = "ISO 4217 target currency", example = "EUR", required = true)
            @RequestParam @Pattern(regexp = "^[A-Z]{3}$", message = "Must be 3 uppercase letters") String to,

            @Parameter(description = "Amount to convert (max 2 decimal places)", example = "1000.00", required = true)
            @RequestParam double amount) {

        ConversionRequest request = ConversionRequest.builder()
                .from(from)
                .to(to)
                .amount(amount)
                .build();

        ConversionResponse response = exchangeRateService.convert(request);
        return ResponseEntity.ok(response);
    }

    @Operation(
        summary     = "List all supported currencies and their rates for a given base",
        description = "Returns the full rate map (150+ currencies) relative to the specified base currency. "
                    + "Rates are cached — the response may be up to 1 hour old."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Rate map returned successfully"),
        @ApiResponse(responseCode = "422", description = "Unsupported base currency",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "External API unavailable",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/currencies/{base}")
    public ResponseEntity<Map<String, Object>> getSupportedCurrencies(
            @Parameter(description = "ISO 4217 base currency", example = "USD", required = true)
            @PathVariable @Pattern(regexp = "^[A-Z]{3}$", message = "Must be 3 uppercase letters") String base) {

        Map<String, Double> rates = exchangeRateService.getSupportedCurrencies(base);

        return ResponseEntity.ok(Map.of(
                "base", base,
                "total", rates.size(),
                "rates", rates
        ));
    }

    @Operation(
        summary     = "Lightweight rate-provider connectivity check",
        description = "Fetches the rate for USD/EUR as a sentinel check. "
                    + "Returns the provider status without performing an actual conversion."
    )
    @GetMapping("/health/rates")
    public ResponseEntity<Map<String, Object>> ratesHealthCheck() {
        try {
            ConversionResponse probe = exchangeRateService.convert(
                    ConversionRequest.builder().from("USD").to("EUR").amount(1.0).build());
            return ResponseEntity.ok(Map.of(
                    "status", "UP",
                    "provider", "ExchangeRate-API v6",
                    "rateLastUpdated", probe.getRateLastUpdated().toString(),
                    "cached", probe.isCached()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of(
                    "status", "DOWN",
                    "reason", e.getMessage()
            ));
        }
    }
}

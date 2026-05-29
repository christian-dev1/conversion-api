package com.elite.currency.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ConversionRequest", description = "Currency conversion input parameters")
public class ConversionRequest {

    @NotBlank(message = "Source currency (from) must not be blank")
    @Size(min = 3, max = 3, message = "Currency code must be exactly 3 characters (ISO 4217)")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency code must be 3 uppercase letters (e.g. USD, EUR)")
    @Schema(description = "ISO 4217 source currency code", example = "USD", requiredMode = Schema.RequiredMode.REQUIRED)
    private String from;

    @NotBlank(message = "Target currency (to) must not be blank")
    @Size(min = 3, max = 3, message = "Currency code must be exactly 3 characters (ISO 4217)")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency code must be 3 uppercase letters (e.g. USD, EUR)")
    @Schema(description = "ISO 4217 target currency code", example = "EUR", requiredMode = Schema.RequiredMode.REQUIRED)
    private String to;

    @NotNull(message = "Amount must not be null")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "999999999.99", message = "Amount must not exceed 999,999,999.99")
    @Digits(integer = 9, fraction = 2, message = "Amount must have at most 9 integer digits and 2 decimal places")
    @Schema(description = "Amount to convert", example = "1000.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private Double amount;
}

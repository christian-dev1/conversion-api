package com.elite.currency.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ErrorResponse", description = "Standard error response body")
public class ErrorResponse {

    @Schema(description = "HTTP status code", example = "400")
    private int status;

    @Schema(description = "Short error category", example = "VALIDATION_ERROR")
    private String error;

    @Schema(description = "Human-readable description of the problem", example = "Request validation failed")
    private String message;

    @Schema(description = "Requested path", example = "/api/v1/convert")
    private String path;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Schema(description = "UTC timestamp of the error", example = "2024-03-15T10:05:30Z")
    private Instant timestamp;

    @Schema(description = "Field-level validation violations (present only for 400 errors)")
    private List<FieldViolation> violations;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "FieldViolation")
    public static class FieldViolation {
        @Schema(example = "amount")
        private String field;
        @Schema(example = "Amount must be greater than 0")
        private String message;
        @Schema(example = "-5")
        private Object rejectedValue;
    }
}

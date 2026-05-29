# 💱 Currency Conversion API

> **Enterprise-grade** REST API for real-time currency conversion — built with Spring Boot 3, WebClient, Resilience4j, and OpenAPI 3.

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.2.4-green?logo=spring)
![OpenAPI](https://img.shields.io/badge/OpenAPI-3.0-blue?logo=swagger)
![License](https://img.shields.io/badge/License-MIT-yellow)

---

## Table of Contents

1. [Architecture](#architecture)
2. [Features](#features)
3. [Prerequisites](#prerequisites)
4. [Quick Start](#quick-start)
5. [API Endpoints](#api-endpoints)
6. [Testing the API](#testing-the-api)
7. [Configuration Reference](#configuration-reference)
8. [Design Decisions](#design-decisions)
9. [Resilience Strategy](#resilience-strategy)
10. [Running Tests](#running-tests)

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    Currency Conversion API                       │
│                                                                  │
│  ┌──────────────┐    ┌──────────────────┐    ┌───────────────┐  │
│  │  Controller  │───▶│     Service      │───▶│  WebClient    │  │
│  │  (REST Layer)│    │  (Business Logic)│    │  (HTTP Client)│  │
│  └──────────────┘    └────────┬─────────┘    └───────┬───────┘  │
│                               │                       │          │
│                     ┌─────────▼─────────┐             │          │
│                     │  Caffeine Cache   │             │          │
│                     │  (TTL: 1 hour)    │             │          │
│                     └───────────────────┘             │          │
│                                                        ▼          │
└────────────────────────────────────────────────────────────────┘
                                                         │
                                              ┌──────────▼──────────┐
                                              │  ExchangeRate-API v6 │
                                              │  (external provider) │
                                              └─────────────────────┘
```

**Resilience layers** wrap the WebClient call:
```
Request → Circuit Breaker → Retry (max 3) → WebClient → Provider
```

---

## Features

| Feature | Implementation |
|---|---|
| Real-time rates | ExchangeRate-API v6 (150+ currencies) |
| Rate caching | Caffeine (TTL: 1 hour per base currency) |
| Circuit Breaker | Resilience4j — opens after 50% failure rate |
| Automatic Retry | 3 attempts with 500 ms backoff |
| Input Validation | Jakarta Validation (Bean Validation 3.0) |
| API Documentation | OpenAPI 3 / Swagger UI |
| Error Handling | RFC 7807-inspired structured errors |
| Observability | Spring Actuator (health, metrics, cache stats) |
| Tests | Unit (MockWebServer) + Slice (@WebMvcTest) |

---

## Prerequisites

| Tool | Version |
|---|---|
| Java | 21+ |
| Maven | 3.9+ |
| ExchangeRate-API key | Free tier at [exchangerate-api.com](https://www.exchangerate-api.com/) |

> **Get a free API key:** Register at https://www.exchangerate-api.com — the free tier provides 1,500 requests/month.

---

## Quick Start

### 1. Clone the repository

```bash
git clone https://github.com/elite/currency-conversion-api.git
cd currency-conversion-api
```

### 2. Set your API key

```bash
# Option A — environment variable (recommended)
export EXCHANGE_RATE_API_KEY=your_api_key_here

# Option B — application.yml (for local dev only, never commit keys)
# Edit src/main/resources/application.yml
# exchange-rate.api.api-key: your_api_key_here
```

### 3. Run the application

```bash
./mvnw spring-boot:run
```

The API starts on **http://localhost:8080**

### 4. Open Swagger UI

```
http://localhost:8080/swagger-ui.html
```

---

## API Endpoints

### Base URL: `http://localhost:8080/api/v1`

| Method | Path | Description |
|---|---|---|
| `POST` | `/convert` | Convert amount (JSON body) |
| `GET` | `/convert` | Convert amount (query params) |
| `GET` | `/currencies/{base}` | List all rates for a base currency |
| `GET` | `/health/rates` | Provider connectivity check |

### Actuator (observability)

| Path | Description |
|---|---|
| `/actuator/health` | Full health status |
| `/actuator/caches` | Cache hit/miss stats |
| `/actuator/metrics/resilience4j.circuitbreaker.state` | Circuit breaker state |

---

## Testing the API

### Option 1 — Swagger UI (recommended)

Navigate to http://localhost:8080/swagger-ui.html and use the **Try it out** button on any endpoint.

### Option 2 — cURL

#### POST /convert

```bash
curl -X POST http://localhost:8080/api/v1/convert \
  -H "Content-Type: application/json" \
  -d '{
    "from": "USD",
    "to": "EUR",
    "amount": 1000.00
  }'
```

Expected response:
```json
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
```

#### GET /convert (query params)

```bash
curl "http://localhost:8080/api/v1/convert?from=USD&to=XAF&amount=500"
```

#### List all currencies relative to EUR

```bash
curl http://localhost:8080/api/v1/currencies/EUR
```

#### Validate error handling — unknown currency

```bash
curl -X POST http://localhost:8080/api/v1/convert \
  -H "Content-Type: application/json" \
  -d '{"from":"USD","to":"XYZ","amount":100}'
```

Expected (422):
```json
{
  "status": 422,
  "error": "INVALID_CURRENCY",
  "message": "Unsupported or unknown currency code: 'XYZ'...",
  "path": "/api/v1/convert",
  "timestamp": "2024-03-15T10:05:30Z"
}
```

#### Validate error handling — negative amount

```bash
curl -X POST http://localhost:8080/api/v1/convert \
  -H "Content-Type: application/json" \
  -d '{"from":"USD","to":"EUR","amount":-50}'
```

Expected (400):
```json
{
  "status": 400,
  "error": "VALIDATION_ERROR",
  "violations": [
    {
      "field": "amount",
      "message": "Amount must be greater than 0",
      "rejectedValue": -50.0
    }
  ]
}
```

### Option 3 — HTTPie

```bash
# Install: https://httpie.io/
http POST :8080/api/v1/convert from=USD to=XAF amount:=1000
```

---

## Configuration Reference

All settings are in `src/main/resources/application.yml`.

| Property | Default | Description |
|---|---|---|
| `exchange-rate.api.api-key` | `YOUR_API_KEY_HERE` | ExchangeRate-API v6 key |
| `exchange-rate.api.base-url` | `https://v6.exchangerate-api.com/v6` | Provider base URL |
| `exchange-rate.api.timeout-seconds` | `5` | WebClient connect/read timeout |
| `spring.cache.caffeine.spec` | `maximumSize=500,expireAfterWrite=3600s` | Cache TTL |
| `resilience4j.circuitbreaker.instances.exchangeRateService.failure-rate-threshold` | `50` | % failures before circuit opens |
| `resilience4j.retry.instances.exchangeRateService.max-attempts` | `3` | Max retry count |

---

## Design Decisions

### Why Caffeine and not Redis?
This is a **single-instance** stateless service. Caffeine provides in-process caching with zero infrastructure overhead, sub-microsecond latency, and LRU eviction. For a clustered deployment, swapping to Redis requires only changing the `spring.cache.type` property and adding the Redis starter — the `@Cacheable` annotations are unchanged.

### Why POST **and** GET for /convert?
The `POST /convert` endpoint follows REST best practices for operations with a structured payload. The `GET /convert` endpoint exists purely for developer convenience (browser URL bar, curl without `-d`). In production, the GET variant would typically be removed or secured.

### Why 422 for invalid currency (not 400)?
`400 Bad Request` signals a **syntactically** malformed request (wrong format, missing field). `422 Unprocessable Entity` signals a **semantically** invalid request — the payload is valid JSON and structurally correct, but the business rule (currency must be supported) is violated. This distinction follows RFC 9110.

### Rounding strategy
All monetary values are rounded to **4 decimal places** using the `Math.round(x * 10_000d) / 10_000d` idiom. This is intentionally not `BigDecimal`-based for performance, since this API handles read-only rate lookups, not financial ledger entries. For a payment processing system, `BigDecimal` with `RoundingMode.HALF_EVEN` would be mandatory.

---

## Resilience Strategy

```
┌─────────────────── Circuit Breaker ────────────────────────┐
│  Sliding window: 10 calls                                   │
│  Opens when: ≥ 50% of last 10 calls fail                   │
│  Wait in OPEN state: 30 seconds                             │
│  Half-open probes: 3 calls before closing                   │
└─────────────────────────────────────────────────────────────┘
         ↑ wraps
┌─────────────────── Retry ───────────────────────────────────┐
│  Max attempts: 3                                             │
│  Backoff: 500 ms fixed                                       │
│  Retried exceptions: WebClientException                      │
└─────────────────────────────────────────────────────────────┘
```

---

## Running Tests

```bash
# All tests
./mvnw test

# Unit tests only (fast, no network)
./mvnw test -pl . -Dtest="ExchangeRateServiceTest"

# Controller slice tests
./mvnw test -pl . -Dtest="CurrencyConversionControllerTest"

# With coverage report (target/site/jacoco/index.html)
./mvnw test jacoco:report
```

---

## Project Structure

```
src/
├── main/java/com/elite/currency/
│   ├── CurrencyConversionApiApplication.java   # Entry point
│   ├── config/
│   │   ├── CacheConfig.java                    # Caffeine configuration
│   │   ├── OpenApiConfig.java                  # Swagger metadata
│   │   └── WebClientConfig.java                # WebClient + timeouts
│   ├── controller/
│   │   └── CurrencyConversionController.java   # REST endpoints
│   ├── dto/
│   │   ├── ConversionRequest.java              # Input payload
│   │   ├── ConversionResponse.java             # Output payload
│   │   └── ErrorResponse.java                  # Error envelope
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java         # @RestControllerAdvice
│   │   ├── ExternalApiException.java
│   │   └── InvalidCurrencyException.java
│   ├── model/
│   │   └── ExchangeRateApiResponse.java        # Provider JSON model
│   └── service/
│       └── ExchangeRateService.java            # Core business logic
└── test/java/com/elite/currency/
    ├── controller/
    │   └── CurrencyConversionControllerTest.java
    └── service/
        └── ExchangeRateServiceTest.java
```

---

## License

MIT © Elite Engineering

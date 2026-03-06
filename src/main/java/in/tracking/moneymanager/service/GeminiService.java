package in.tracking.moneymanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for interacting with Google Gemini AI.
 * Includes rate limiting and retry logic to respect free tier limits.
 *
 * Free Tier Limits (Gemini 2.5 Flash - March 2026):
 * - 15 requests per minute (RPM)
 * - 250 requests per day (RPD)
 *
 * Get your API key from: https://aistudio.google.com/app/apikey
 */
@Service
@Slf4j
public class GeminiService {

    private final WebClient webClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;

    // Rate limiter: Conservative - 10 requests per minute (0.167 permits/second)
    // Free tier allows 15 RPM, we use 10 to have buffer
    private final RateLimiter rateLimiter = RateLimiter.create(0.167);

    // Track consecutive failures for circuit breaker pattern
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long circuitBreakerResetTime = 0;

    // Max retries for rate limit errors
    private static final int MAX_RETRIES = 2;
    private static final long INITIAL_BACKOFF_MS = 5000; // 5 seconds
    private static final long CIRCUIT_BREAKER_TIMEOUT_MS = 120000; // 2 minutes

    public GeminiService(
            @Value("${gemini.api.key}") String apiKey,
            @Value("${gemini.model}") String model,
            @Value("${gemini.api.url}") String apiUrl) {

        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = new ObjectMapper();

        this.webClient = WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Content-Type", "application/json")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(5 * 1024 * 1024))
                .build();

        log.info("GeminiService initialized with model: {}", model);
    }

    /**
     * Generate a response from Gemini AI.
     * Automatically handles rate limiting and retries.
     *
     * @param prompt The prompt to send to Gemini
     * @return AI-generated response text
     */
    public String generateResponse(String prompt) {
        // Check circuit breaker
        if (isCircuitOpen()) {
            long waitSeconds = (circuitBreakerResetTime - System.currentTimeMillis()) / 1000;
            log.warn("Circuit breaker is open. Please wait {} seconds.", waitSeconds);
            throw new RuntimeException("AI service is temporarily unavailable due to rate limiting. Please try again in " + waitSeconds + " seconds.");
        }

        // Wait for rate limiter permit
        double waitTime = rateLimiter.acquire();
        if (waitTime > 0) {
            log.debug("Rate limited, waited {}s for permit", waitTime);
        }

        return executeWithRetry(prompt, 0);
    }

    /**
     * Execute API call with exponential backoff retry.
     */
    private String executeWithRetry(String prompt, int attempt) {
        long startTime = System.currentTimeMillis();

        try {
            // Build request body according to Gemini API format
            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of("parts", List.of(
                                    Map.of("text", prompt)
                            ))
                    ),
                    "generationConfig", Map.of(
                            "temperature", 0.7,        // Creativity level (0-1)
                            "maxOutputTokens", 2048,   // Max response length
                            "topP", 0.95,              // Nucleus sampling
                            "topK", 40                 // Top-k sampling
                    )
            );

            // Call Gemini API
            String response = webClient.post()
                    .uri("/{model}:generateContent?key={key}", model, apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(30));

            long duration = System.currentTimeMillis() - startTime;
            log.info("Gemini API response received in {}ms (attempt {})", duration, attempt + 1);

            // Reset failure counter on success
            consecutiveFailures.set(0);

            // Extract text from response
            return extractTextFromResponse(response);

        } catch (WebClientResponseException.TooManyRequests e) {
            consecutiveFailures.incrementAndGet();

            if (attempt < MAX_RETRIES) {
                long backoffMs = INITIAL_BACKOFF_MS * (long) Math.pow(2, attempt);
                log.warn("Rate limited (429). Retry {} of {} in {}ms", attempt + 1, MAX_RETRIES, backoffMs);

                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for retry", ie);
                }

                return executeWithRetry(prompt, attempt + 1);
            } else {
                // Open circuit breaker for 2 minutes
                circuitBreakerResetTime = System.currentTimeMillis() + CIRCUIT_BREAKER_TIMEOUT_MS;
                long waitMinutes = CIRCUIT_BREAKER_TIMEOUT_MS / 60000;
                log.error("Gemini API rate limited after {} retries. Circuit breaker opened for {} minutes.", MAX_RETRIES, waitMinutes);
                throw new RuntimeException("AI service is busy due to rate limiting. Please try again in " + waitMinutes + " minutes.", e);
            }
        } catch (WebClientResponseException.NotFound e) {
            log.error("Gemini API model not found (404). Model: {}. The model may have been deprecated.", model);
            throw new RuntimeException("AI model not available. Please contact support to update the AI configuration.", e);
        } catch (WebClientResponseException.Unauthorized e) {
            log.error("Gemini API unauthorized (401). API key may be invalid.");
            throw new RuntimeException("AI service authentication failed. Please check the API key.", e);
        } catch (WebClientResponseException e) {
            log.error("Gemini API error: {} - {}", e.getStatusCode(), e.getMessage());
            throw new RuntimeException("AI service error: " + e.getStatusText(), e);
        } catch (Exception e) {
            log.error("Gemini API error: {}", e.getMessage());
            throw new RuntimeException("Failed to get AI response. Please try again later.", e);
        }
    }

    /**
     * Check if circuit breaker is open (too many failures).
     */
    private boolean isCircuitOpen() {
        if (circuitBreakerResetTime > System.currentTimeMillis()) {
            return true;
        }
        // Reset circuit breaker if time has passed
        if (circuitBreakerResetTime > 0 && circuitBreakerResetTime <= System.currentTimeMillis()) {
            circuitBreakerResetTime = 0;
            consecutiveFailures.set(0);
            log.info("Circuit breaker reset. AI service available again.");
        }
        return false;
    }

    /**
     * Extract text content from Gemini API JSON response.
     * Response format: {"candidates":[{"content":{"parts":[{"text":"..."}]}}]}
     */
    private String extractTextFromResponse(String jsonResponse) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);

            // Navigate to text content
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode content = candidates.get(0).path("content");
                JsonNode parts = content.path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    return parts.get(0).path("text").asText();
                }
            }

            // Check for error response
            JsonNode error = root.path("error");
            if (!error.isMissingNode()) {
                String errorMessage = error.path("message").asText();
                log.error("Gemini API error response: {}", errorMessage);
                throw new RuntimeException("AI service error: " + errorMessage);
            }

            log.warn("Unexpected Gemini response format: {}", jsonResponse);
            return "Unable to generate response. Please try again.";

        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", e.getMessage());
            return "Unable to process AI response. Please try again.";
        }
    }
}


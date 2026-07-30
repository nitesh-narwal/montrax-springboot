package in.tracking.moneymanager.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Sends OTP SMS via TextBee (https://textbee.dev) gateway.
 * No-ops (throws) when device id/API key are missing so the app still boots
 * without SMS/OTP support.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TextBeeSmsService {

    private final AppCacheService appCacheService;

    private WebClient buildClient(String apiUrl) {
        return WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public void sendSms(String toPhoneNumber, String body) {
        String apiKey = appCacheService.get("textbee.api.key");
        String deviceId = appCacheService.get("textbee.device.id");
        String apiUrl = appCacheService.get("textbee.api.url", "https://api.textbee.dev/api/v1/gateway");

        if (apiKey == null || apiKey.isBlank() || deviceId == null || deviceId.isBlank()) {
            log.warn("TextBee not configured. Skipping SMS send to: {}", toPhoneNumber);
            throw new IllegalStateException("SMS delivery is not configured on this server");
        }

        try {
            buildClient(apiUrl).post()
                    .uri("/devices/{deviceId}/send-sms", deviceId)
                    .header("x-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "recipients", List.of(toPhoneNumber),
                            "message", body
                    ))
                    .retrieve()
                    .toBodilessEntity()
                    .block();

            log.info("SMS sent to {} via TextBee", toPhoneNumber);
        } catch (WebClientResponseException e) {
            log.warn("TextBee SMS delivery failed for {}: {} {}", toPhoneNumber, e.getStatusCode(), e.getResponseBodyAsString());
            throw new IllegalStateException("SMS delivery failed", e);
        }
    }
}

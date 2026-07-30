package in.tracking.moneymanager.service;

import in.tracking.moneymanager.entity.ProfileEntity;
import in.tracking.moneymanager.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Phone OTP generation/verification backed by Redis.
 * Codes expire in 5 minutes; resends are throttled to one per 60 seconds;
 * verification attempts are capped at 5 per code to resist brute force.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private static final String OTP_KEY_PREFIX = "otp:";
    private static final String COOLDOWN_KEY_PREFIX = "otp_cooldown:";
    private static final String ATTEMPTS_KEY_PREFIX = "otp_attempts:";

    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;

    private final RedisTemplate<String, Object> redisTemplate;
    private final TextBeeSmsService textBeeSmsService;
    private final EmailService emailService;
    private final ProfileRepository profileRepository;
    private final AppCacheService appCacheService;
    private final SecureRandom secureRandom = new SecureRandom();

    private String getFrontendURL() {
        return appCacheService.get("money.manager.frontend.url", "http://localhost:5173");
    }

    public void sendOtp(String phoneNumber) {
        String cooldownKey = COOLDOWN_KEY_PREFIX + phoneNumber;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Please wait before requesting another OTP");
        }

        String code = String.valueOf(100000 + secureRandom.nextInt(900000));

        redisTemplate.opsForValue().set(OTP_KEY_PREFIX + phoneNumber, code, OTP_TTL);
        redisTemplate.opsForValue().set(cooldownKey, "1", RESEND_COOLDOWN);
        redisTemplate.delete(ATTEMPTS_KEY_PREFIX + phoneNumber);

        try {
            textBeeSmsService.sendSms(phoneNumber, "Your Money Manager verification code is: " + code
                    + ". It expires in 5 minutes.");
            log.info("OTP sent via SMS to phone: {}", phoneNumber);
        } catch (RuntimeException smsException) {
            log.warn("SMS delivery failed for {}: {}. Falling back to email.", phoneNumber, smsException.getMessage());
            sendOtpEmailFallback(phoneNumber, code, smsException);
        }
    }

    /**
     * phone_number has no DB uniqueness constraint, so multiple profiles can share one
     * (e.g. abandoned signups reusing a test number). Prefer the newest unverified profile -
     * that's the one actively going through OTP - falling back to the newest overall.
     */
    private Optional<ProfileEntity> resolveProfileForPhone(String phoneNumber) {
        List<ProfileEntity> profiles = profileRepository.findByPhoneNumberOrderByCreatedAtDesc(phoneNumber);
        return profiles.stream()
                .filter(p -> !Boolean.TRUE.equals(p.getIsPhoneVerified()))
                .findFirst()
                .or(() -> profiles.stream().findFirst());
    }

    private void sendOtpEmailFallback(String phoneNumber, String code, RuntimeException smsException) {
        ProfileEntity profile = resolveProfileForPhone(phoneNumber).orElse(null);

        if (profile == null || profile.getEmail() == null || profile.getEmail().isBlank()) {
            throw smsException;
        }

        String activationLink = profile.getActivationToken() != null
                ? getFrontendURL() + "/activate/" + profile.getActivationToken()
                : null;

        emailService.sendEmail(profile.getEmail(), "Your Money Manager verification code",
                buildOtpEmailBody(profile.getFullname(), code, activationLink));
        log.info("OTP sent via email fallback to {} (phone: {})", profile.getEmail(), phoneNumber);
    }

    private String buildOtpEmailBody(String fullname, String code, String activationLink) {
        String greetingName = fullname != null && !fullname.isBlank() ? fullname : "there";

        String activationBlock = activationLink == null ? "" :
                "<p>You can also activate your account directly by clicking the button below "
                        + "(this also confirms your phone number, so you can skip entering the code above):</p>"
                        + "<p style='text-align: center; margin: 30px 0;'>"
                        + "<a href='" + activationLink + "' style='background-color: #4CAF50; color: white; "
                        + "padding: 12px 24px; text-decoration: none; border-radius: 5px; font-weight: bold;'>"
                        + "Activate My Account</a></p>"
                        + "<p>Or copy and paste this link in your browser:</p>"
                        + "<p style='word-break: break-all; color: #666;'>" + activationLink + "</p>";

        return "<html><body>"
                + "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;'>"
                + "<h2 style='color: #333;'>Welcome to Money Manager</h2>"
                + "<p>Hello " + greetingName + ",</p>"
                + "<p>We couldn't reach your phone by SMS, so here's your verification code by email instead:</p>"
                + "<div style='background-color: #f5f5f5; border-radius: 5px; padding: 20px; margin: 20px 0; text-align: center;'>"
                + "<span style='font-size: 32px; font-weight: bold; letter-spacing: 6px; color: #333;'>" + code + "</span>"
                + "</div>"
                + "<p>Enter this code in the app to verify your phone number. "
                + "<strong>It expires in 5 minutes.</strong></p>"
                + activationBlock
                + "<p>If you didn't request this code, you can safely ignore this email.</p>"
                + "<hr style='border: none; border-top: 1px solid #eee; margin: 30px 0;'>"
                + "<p style='color: #888; font-size: 12px;'>This is an automated message from Money Manager. Please do not reply to this email.</p>"
                + "</div>"
                + "</body></html>";
    }

    @Transactional
    public boolean verifyOtp(String phoneNumber, String code) {
        String otpKey = OTP_KEY_PREFIX + phoneNumber;
        String attemptsKey = ATTEMPTS_KEY_PREFIX + phoneNumber;

        Object storedCode = redisTemplate.opsForValue().get(otpKey);
        if (storedCode == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP expired or not found. Please request a new one.");
        }

        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(attemptsKey, OTP_TTL);
        }
        if (attempts != null && attempts > MAX_ATTEMPTS) {
            redisTemplate.delete(otpKey);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many failed attempts. Please request a new OTP.");
        }

        if (!storedCode.toString().equals(code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid OTP");
        }

        redisTemplate.delete(otpKey);
        redisTemplate.delete(attemptsKey);

        resolveProfileForPhone(phoneNumber).ifPresent(profile -> {
            profile.setIsPhoneVerified(true);
            // Knowing the OTP is as strong a proof of ownership as clicking the emailed
            // activation link, so verifying the phone also clears the activation gate -
            // otherwise a user who never got the fallback email would be stuck forever.
            profile.setIsActive(true);
            profile.setActivationToken(null);
            profileRepository.save(profile);
            log.info("Phone verified for profile: {}", profile.getEmail());
        });

        return true;
    }
}

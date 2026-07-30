package in.tracking.moneymanager.controller;

import in.tracking.moneymanager.dto.AuthDTO;
import in.tracking.moneymanager.dto.ForgotPasswordDTO;
import in.tracking.moneymanager.dto.ProfileDTO;
import in.tracking.moneymanager.dto.ResetPasswordDTO;
import in.tracking.moneymanager.service.CloudinaryService;
import in.tracking.moneymanager.service.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    private static final String OAUTH2_EXCHANGE_KEY_PREFIX = "oauth2_exchange:";

    private final ProfileService profileService;
    private final CloudinaryService cloudinaryService;
    private final RedisTemplate<String, Object> redisTemplate;

    @PostMapping("/register")
    public ResponseEntity<ProfileDTO> registerProfile(@RequestBody ProfileDTO profileDTO) {
        ProfileDTO registeredProfile = profileService.registerProfile(profileDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(registeredProfile);
    }

    @GetMapping("/activate/{token}")
    public ResponseEntity<String> activateProfile(@PathVariable String token) {
        boolean isActivated = profileService.activateProfile(token);
        if (isActivated) {
            return ResponseEntity.ok("Profile activated successfully");
        }else{
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Activation token NOT FOUND or ALREADY USED");
        }
    }

    /**
     * Resend activation email for users who didn't receive the first one.
     * POST /resend-activation
     */
    @PostMapping("/resend-activation")
    public ResponseEntity<Map<String, Object>> resendActivationEmail(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Email is required"
            ));
        }
        Map<String, Object> result = profileService.resendActivationEmail(email.trim());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody AuthDTO authDTO) {
        try {
            Map<String, Object> response =
                    profileService.authenticateAndGenerateToken(authDTO);

            if (!profileService.isAccountActive(authDTO.getEmail())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("message", "Account is not activated."));
            }

            return ResponseEntity.ok(response);

        } catch (BadCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid email or password"));
        } catch (org.springframework.web.server.ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode())
                    .body(Map.of("message", e.getReason()));
        } catch (Exception e) {
            log.error("Unexpected error during login for {}", authDTO.getEmail(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Login failed"));
        }
    }

    /**
     * Exchanges a one-time OAuth2 login code (issued via the /oauth2/redirect
     * query param) for the real JWT. Single-use and short-lived (60s) so the
     * actual bearer token never appears in a URL, browser history, or logs.
     *
     * POST /oauth2/exchange
     */
    @PostMapping("/oauth2/exchange")
    public ResponseEntity<Map<String, Object>> exchangeOAuth2Code(@RequestBody Map<String, String> request) {
        String code = request.get("code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Missing code"));
        }

        String key = OAUTH2_EXCHANGE_KEY_PREFIX + code;
        Object token = redisTemplate.opsForValue().get(key);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Invalid or expired code"));
        }
        redisTemplate.delete(key);

        return ResponseEntity.ok(Map.of("token", token.toString()));
    }

    /**
     * Get current user's profile.
     */
    @GetMapping("/profile")
    public ResponseEntity<ProfileDTO> getProfile() {
        return ResponseEntity.ok(profileService.getPublicProfile(null));
    }

    /**
     * Upload profile image to Cloudinary.
     *
     * POST /profile/image
     */
    @PostMapping("/profile/image")
    public ResponseEntity<Map<String, Object>> uploadProfileImage(@RequestParam("file") MultipartFile file) {
        try {
            // Get current user's profile
            Long userId = profileService.getCurrentProfile().getId();

            boolean hadPreviousImage = cloudinaryService.profileImageExists(userId);

            // Upload to Cloudinary
            String imageUrl = cloudinaryService.uploadProfileImage(file, userId);

            // Update profile with new image URL
            ProfileDTO updatedProfile = profileService.updateProfileImageUrl(imageUrl);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Profile image uploaded successfully",
                    "hadPreviousImage", hadPreviousImage,
                "profileImageUrl", imageUrl,
                "profile", updatedProfile
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "success", false,
                "message", "Failed to upload image: " + e.getMessage()
            ));
        }
    }

    /**
     * Update the current user's preferred notification time for budget alerts
     * and bill reminders. Send { "time": null } to revert to the default time.
     *
     * PUT /profile/notification-time
     * Body: { "time": "21:30" }
     */
    @PutMapping("/profile/notification-time")
    public ResponseEntity<ProfileDTO> updateNotificationTime(@RequestBody Map<String, String> request) {
        String time = request.get("time");
        java.time.LocalTime notificationTime = (time == null || time.isBlank())
                ? null
                : java.time.LocalTime.parse(time);
        return ResponseEntity.ok(profileService.updateNotificationTime(notificationTime));
    }

    // ==================== ACCOUNT DELETION ENDPOINTS ====================

    /**
     * Request account deletion with 3-day grace period.
     * User can cancel by logging in again within 3 days.
     *
     * POST /api/account/delete-request
     */
    @PostMapping("/api/account/delete-request")
    public ResponseEntity<Map<String, Object>> requestAccountDeletion() {
        Map<String, Object> result = profileService.requestAccountDeletion();
        boolean success = (boolean) result.get("success");
        return success ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    /**
     * Cancel pending account deletion.
     *
     * POST /api/account/cancel-deletion
     */
    @PostMapping("/api/account/cancel-deletion")
    public ResponseEntity<Map<String, Object>> cancelAccountDeletion() {
        Map<String, Object> result = profileService.cancelAccountDeletionRequest();
        boolean success = (boolean) result.get("success");
        return success ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    /**
     * Get account deletion status.
     *
     * GET /api/account/deletion-status
     */
    @GetMapping("/api/account/deletion-status")
    public ResponseEntity<Map<String, Object>> getAccountDeletionStatus() {
        return ResponseEntity.ok(profileService.getAccountDeletionStatus());
    }

    // ==================== PASSWORD RESET ENDPOINTS ====================

    /**
     * Request password reset - sends email with reset link.
     * This is a public endpoint.
     *
     * POST /forgot-password
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(@RequestBody ForgotPasswordDTO forgotPasswordDTO) {
        Map<String, Object> result = profileService.requestPasswordReset(forgotPasswordDTO.getEmail());
        return ResponseEntity.ok(result);
    }

    /**
     * Reset password using the reset token.
     * This is a public endpoint.
     *
     * POST /reset-password
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody ResetPasswordDTO resetPasswordDTO) {
        Map<String, Object> result = profileService.resetPassword(
            resetPasswordDTO.getToken(),
            resetPasswordDTO.getNewPassword()
        );
        boolean success = (boolean) result.get("success");
        return success ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }

    /**
     * Validate a password reset token.
     * This is a public endpoint.
     *
     * GET /validate-reset-token?token={token}
     */
    @GetMapping("/validate-reset-token")
    public ResponseEntity<Map<String, Object>> validateResetToken(@RequestParam String token) {
        Map<String, Object> result = profileService.validateResetToken(token);
        boolean valid = (boolean) result.get("valid");
        return valid ? ResponseEntity.ok(result) : ResponseEntity.badRequest().body(result);
    }
}

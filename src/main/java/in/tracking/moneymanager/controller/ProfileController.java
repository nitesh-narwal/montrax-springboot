package in.tracking.moneymanager.controller;

import in.tracking.moneymanager.dto.AuthDTO;
import in.tracking.moneymanager.dto.ForgotPasswordDTO;
import in.tracking.moneymanager.dto.ProfileDTO;
import in.tracking.moneymanager.dto.ResetPasswordDTO;
import in.tracking.moneymanager.service.CloudinaryService;
import in.tracking.moneymanager.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final CloudinaryService cloudinaryService;

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

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody AuthDTO authDTO) {
        try {
            System.out.println("Account active: " +
                    profileService.isAccountActive(authDTO.getEmail()));

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
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "Login failed"));
        }
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

            // Upload to Cloudinary
            String imageUrl = cloudinaryService.uploadProfileImage(file, userId);

            // Update profile with new image URL
            ProfileDTO updatedProfile = profileService.updateProfileImageUrl(imageUrl);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Profile image uploaded successfully",
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

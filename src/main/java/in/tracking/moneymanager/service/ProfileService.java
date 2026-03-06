package in.tracking.moneymanager.service;

import in.tracking.moneymanager.dto.AuthDTO;
import in.tracking.moneymanager.dto.ProfileDTO;
import in.tracking.moneymanager.entity.ProfileEntity;
import in.tracking.moneymanager.repository.ProfileRepository;
import in.tracking.moneymanager.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.ProviderNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private final EmailService emailService;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @Value( "${app.activation.url}")
    private String activationURL;

    @Value("${app.password-reset.url:${app.activation.url}}")
    private String passwordResetURL;

    // Grace period in days before permanent deletion
    private static final int DELETION_GRACE_PERIOD_DAYS = 3;

    // Password reset token expiry in hours
    private static final int PASSWORD_RESET_TOKEN_EXPIRY_HOURS = 1;

    public ProfileDTO registerProfile(ProfileDTO profileDTO) {
        ProfileEntity newProfile = toEntity(profileDTO);
        newProfile.setActivationToken(UUID.randomUUID().toString());
        newProfile = profileRepository.save(newProfile);

        //send activation email
        String activationLink = activationURL + "/activate?token=" + newProfile.getActivationToken();
        String subject = "Activate your Money Manager account";
        String body = "Please click on the following link to activate your account: " + activationLink;
        emailService.sendEmail(newProfile.getEmail(), subject, body);
        return toDTO(newProfile);
    }

    public ProfileEntity toEntity(ProfileDTO profileDTO) {
        return ProfileEntity.builder()
                .id(profileDTO.getId())
                .fullname(profileDTO.getFullname())
                .email(profileDTO.getEmail())
                .password(passwordEncoder.encode(profileDTO.getPassword()))
                .profileImageUrl(profileDTO.getProfileImageUrl())
                .createdAt(profileDTO.getCreatedAt())
                .updatedAt(profileDTO.getUpdatedAt())
                .build();
    }

    public ProfileDTO toDTO(ProfileEntity profileEntity) {
        return ProfileDTO.builder()
                .id(profileEntity.getId())
                .fullname(profileEntity.getFullname())
                .email(profileEntity.getEmail())
                .profileImageUrl(profileEntity.getProfileImageUrl())
                .createdAt(profileEntity.getCreatedAt())
                .updatedAt(profileEntity.getUpdatedAt())
                .build();
    }

    public boolean activateProfile(String activationToken) {
        return profileRepository.findByActivationToken(activationToken)
                .map(profile -> {
                    ;
                    profile.setIsActive(true);
                    profileRepository.save(profile);
                    return true;
                })
                .orElse(false);
    }

    public boolean isAccountActive(String email) {
        return profileRepository.findByEmail(email)
                .map(ProfileEntity::getIsActive)
                .orElse(false);
    }

    public ProfileEntity getCurrentProfile() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return profileRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ProviderNotFoundException("Profile not found with email" + authentication.getName()));
    }

    /**
     * Get profile by ID (used by scheduled jobs that don't have auth context).
     */
    public ProfileEntity getProfileById(Long profileId) {
        return profileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("Profile not found with id: " + profileId));
    }

    public ProfileDTO getPublicProfile(String email) {
        ProfileEntity currentUser = null;
        if (email == null) {
            currentUser = getCurrentProfile();
        } else {
            currentUser = profileRepository.findByEmail(email)
                    .orElseThrow(() -> new ProviderNotFoundException("Profile not found with email" + email));
        }

        return ProfileDTO.builder()
                .id(currentUser.getId())
                .fullname(currentUser.getFullname())
                .email(currentUser.getEmail())
                .profileImageUrl(currentUser.getProfileImageUrl())
                .createdAt(currentUser.getCreatedAt())
                .updatedAt(currentUser.getUpdatedAt())
                .build();
    }

    /**
     * Update profile image URL for the current user.
     *
     * @param imageUrl The new image URL from Cloudinary
     * @return Updated profile DTO
     */
    @Transactional
    public ProfileDTO updateProfileImageUrl(String imageUrl) {
        ProfileEntity profile = getCurrentProfile();
        profile.setProfileImageUrl(imageUrl);
        profile = profileRepository.save(profile);
        log.info("Profile image updated for user {}", profile.getId());
        return toDTO(profile);
    }

    public Map<String, Object> authenticateAndGenerateToken(AuthDTO authDTO) {
        try {
            // Check if account is pending deletion and cancel it
            ProfileEntity profile = profileRepository.findByEmail(authDTO.getEmail())
                    .orElseThrow(() -> new RuntimeException("Profile not found"));

            if (Boolean.TRUE.equals(profile.getIsPendingDeletion())) {
                // Cancel deletion on login
                cancelAccountDeletion(profile);
                log.info("Account deletion cancelled for user {} due to login", authDTO.getEmail());
            }

            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(authDTO.getEmail(), authDTO.getPassword()));
            //Generate JWT token
            String token = jwtUtil.generateToken(authDTO.getEmail());

            Map<String, Object> response = new java.util.HashMap<>();
            response.put("token", token);
            response.put("user", getPublicProfile(authDTO.getEmail()));

            // If deletion was pending, inform the user
            if (Boolean.TRUE.equals(profile.getIsPendingDeletion())) {
                response.put("deletionCancelled", true);
                response.put("message", "Your account deletion has been cancelled. Welcome back!");
            }

            return response;

        } catch (Exception e) {
            throw new RuntimeException("Invalid username/password supplied");
        }
    }

    // ==================== ACCOUNT DELETION METHODS ====================

    /**
     * Request account deletion with 3-day grace period.
     * User can cancel deletion by logging in again within 3 days.
     *
     * @return Map with deletion status and scheduled deletion date
     */
    @Transactional
    public Map<String, Object> requestAccountDeletion() {
        ProfileEntity profile = getCurrentProfile();

        if (Boolean.TRUE.equals(profile.getIsPendingDeletion())) {
            return Map.of(
                "success", false,
                "message", "Account deletion is already scheduled",
                "scheduledAt", profile.getDeletionScheduledAt()
            );
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduledDeletion = now.plusDays(DELETION_GRACE_PERIOD_DAYS);

        profile.setIsPendingDeletion(true);
        profile.setDeletionRequestedAt(now);
        profile.setDeletionScheduledAt(scheduledDeletion);
        profileRepository.save(profile);

        // Send email notification
        String subject = "Account Deletion Request - Montrax";
        String body = String.format(
            "<html><body>" +
            "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;'>" +
            "<h2 style='color: #dc3545;'>⚠️ Account Deletion Request</h2>" +
            "<p>Hello %s,</p>" +
            "<p>We received a request to delete your <strong>Montrax</strong> account.</p>" +
            "<div style='background-color: #fff3cd; border: 1px solid #ffc107; border-radius: 5px; padding: 15px; margin: 20px 0;'>" +
            "<p style='margin: 0; color: #856404;'><strong>📅 Scheduled Deletion Date:</strong> %s</p>" +
            "</div>" +
            "<h3 style='color: #333;'>What happens next?</h3>" +
            "<ul style='color: #555;'>" +
            "<li>Your account will remain active for the next <strong>3 days</strong></li>" +
            "<li>After this period, your account and all associated data will be <strong>permanently deleted</strong></li>" +
            "<li>This includes: expenses, incomes, budgets, categories, recurring transactions, and AI insights</li>" +
            "</ul>" +
            "<h3 style='color: #333;'>Changed your mind?</h3>" +
            "<p>Simply <strong>log in to your account</strong> before the scheduled deletion date, and your deletion request will be automatically cancelled.</p>" +
            "<div style='background-color: #f8d7da; border: 1px solid #f5c6cb; border-radius: 5px; padding: 15px; margin: 20px 0;'>" +
            "<p style='margin: 0; color: #721c24;'><strong>⚠️ Important:</strong> Once deleted, your data cannot be recovered.</p>" +
            "</div>" +
            "<p>If you didn't request this deletion, please log in immediately to secure your account and consider changing your password.</p>" +
            "<hr style='border: none; border-top: 1px solid #eee; margin: 30px 0;'>" +
            "<p style='color: #888; font-size: 12px;'>Thank you for using Montrax. We're sorry to see you go!</p>" +
            "<p style='color: #888; font-size: 12px;'>Best regards,<br>The Montrax Team</p>" +
            "</div>" +
            "</body></html>",
            profile.getFullname() != null ? profile.getFullname() : "User",
            scheduledDeletion.toString()
        );
        emailService.sendEmail(profile.getEmail(), subject, body);

        log.info("Account deletion requested for profile: {}. Scheduled at: {}", profile.getId(), scheduledDeletion);

        return Map.of(
            "success", true,
            "message", "Account deletion scheduled. Log in within 3 days to cancel.",
            "deletionRequestedAt", now,
            "scheduledDeletionAt", scheduledDeletion
        );
    }

    /**
     * Cancel pending account deletion.
     *
     * @return Map with cancellation status
     */
    @Transactional
    public Map<String, Object> cancelAccountDeletionRequest() {
        ProfileEntity profile = getCurrentProfile();
        return cancelAccountDeletion(profile);
    }

    /**
     * Internal method to cancel account deletion.
     */
    private Map<String, Object> cancelAccountDeletion(ProfileEntity profile) {
        if (!Boolean.TRUE.equals(profile.getIsPendingDeletion())) {
            return Map.of(
                "success", false,
                "message", "No pending deletion request found"
            );
        }

        profile.setIsPendingDeletion(false);
        profile.setDeletionRequestedAt(null);
        profile.setDeletionScheduledAt(null);
        profileRepository.save(profile);

        log.info("Account deletion cancelled for profile: {}", profile.getId());

        return Map.of(
            "success", true,
            "message", "Account deletion has been cancelled. Your account is safe."
        );
    }

    /**
     * Get account deletion status.
     *
     * @return Map with deletion status details
     */
    public Map<String, Object> getAccountDeletionStatus() {
        ProfileEntity profile = getCurrentProfile();

        if (!Boolean.TRUE.equals(profile.getIsPendingDeletion())) {
            return Map.of(
                "isPendingDeletion", false
            );
        }

        long hoursRemaining = java.time.Duration.between(
            LocalDateTime.now(),
            profile.getDeletionScheduledAt()
        ).toHours();

        return Map.of(
            "isPendingDeletion", true,
            "deletionRequestedAt", profile.getDeletionRequestedAt(),
            "scheduledDeletionAt", profile.getDeletionScheduledAt(),
            "hoursRemaining", Math.max(0, hoursRemaining)
        );
    }

    /**
     * Get all profiles pending deletion that have passed their scheduled time.
     * Used by scheduled job to permanently delete accounts.
     *
     * @return List of profiles to delete
     */
    public List<ProfileEntity> getProfilesScheduledForDeletion() {
        return profileRepository.findByIsPendingDeletionTrueAndDeletionScheduledAtBefore(LocalDateTime.now());
    }

    /**
     * Permanently delete a profile and all associated data.
     * Called by scheduled job after grace period.
     *
     * @param profileId Profile ID to delete
     */
    @Transactional
    public void permanentlyDeleteProfile(Long profileId) {
        ProfileEntity profile = profileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("Profile not found"));

        // Note: This will cascade delete all related entities (expenses, incomes, etc.)
        // Make sure FK constraints have ON DELETE CASCADE or delete related entities first
        profileRepository.delete(profile);

        log.info("Profile {} permanently deleted", profileId);
    }

    // ==================== PASSWORD RESET METHODS ====================

    /**
     * Request password reset - sends email with reset link.
     *
     * @param email User's email address
     * @return Map with status and message
     */
    @Transactional
    public Map<String, Object> requestPasswordReset(String email) {
        // Always return success message to prevent email enumeration attacks
        Map<String, Object> successResponse = Map.of(
            "success", true,
            "message", "If an account with that email exists, we've sent a password reset link."
        );

        try {
            ProfileEntity profile = profileRepository.findByEmail(email).orElse(null);

            if (profile == null) {
                log.warn("Password reset requested for non-existent email: {}", email);
                return successResponse; // Don't reveal that email doesn't exist
            }

            // Generate reset token
            String resetToken = UUID.randomUUID().toString();
            LocalDateTime expiry = LocalDateTime.now().plusHours(PASSWORD_RESET_TOKEN_EXPIRY_HOURS);

            profile.setPasswordResetToken(resetToken);
            profile.setPasswordResetTokenExpiry(expiry);
            profileRepository.save(profile);

            // Send password reset email
            String resetLink = passwordResetURL + "/reset-password?token=" + resetToken;
            String subject = "Reset Your Money Manager Password";
            String body = String.format(
                "<html><body>" +
                "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;'>" +
                "<h2 style='color: #333;'>Password Reset Request</h2>" +
                "<p>Hello %s,</p>" +
                "<p>We received a request to reset your Money Manager password.</p>" +
                "<p>Click the button below to reset your password:</p>" +
                "<p style='text-align: center; margin: 30px 0;'>" +
                "<a href='%s' style='background-color: #4CAF50; color: white; padding: 12px 24px; text-decoration: none; border-radius: 5px; font-weight: bold;'>Reset Password</a>" +
                "</p>" +
                "<p>Or copy and paste this link in your browser:</p>" +
                "<p style='word-break: break-all; color: #666;'>%s</p>" +
                "<p><strong>This link will expire in %d hour(s).</strong></p>" +
                "<p>If you didn't request a password reset, you can safely ignore this email. Your password will remain unchanged.</p>" +
                "<hr style='border: none; border-top: 1px solid #eee; margin: 30px 0;'>" +
                "<p style='color: #888; font-size: 12px;'>This is an automated message from Money Manager. Please do not reply to this email.</p>" +
                "</div>" +
                "</body></html>",
                profile.getFullname() != null ? profile.getFullname() : "User",
                resetLink,
                resetLink,
                PASSWORD_RESET_TOKEN_EXPIRY_HOURS
            );

            emailService.sendEmail(profile.getEmail(), subject, body);
            log.info("Password reset email sent to: {}", email);

            return successResponse;
        } catch (Exception e) {
            log.error("Error processing password reset request for email: {}", email, e);
            return successResponse; // Still return success to prevent enumeration
        }
    }

    /**
     * Reset password using the reset token.
     *
     * @param token Password reset token
     * @param newPassword New password
     * @return Map with status and message
     */
    @Transactional
    public Map<String, Object> resetPassword(String token, String newPassword) {
        if (token == null || token.trim().isEmpty()) {
            return Map.of(
                "success", false,
                "message", "Invalid reset token"
            );
        }

        ProfileEntity profile = profileRepository.findByPasswordResetToken(token).orElse(null);

        if (profile == null) {
            log.warn("Password reset attempted with invalid token");
            return Map.of(
                "success", false,
                "message", "Invalid or expired reset token"
            );
        }

        // Check if token has expired
        if (profile.getPasswordResetTokenExpiry() == null ||
            LocalDateTime.now().isAfter(profile.getPasswordResetTokenExpiry())) {
            log.warn("Password reset attempted with expired token for user: {}", profile.getEmail());
            // Clear the expired token
            profile.setPasswordResetToken(null);
            profile.setPasswordResetTokenExpiry(null);
            profileRepository.save(profile);
            return Map.of(
                "success", false,
                "message", "Reset token has expired. Please request a new password reset."
            );
        }

        // Validate new password
        if (newPassword == null || newPassword.length() < 8) {
            return Map.of(
                "success", false,
                "message", "Password must be at least 8 characters long"
            );
        }

        // Update password and clear reset token
        profile.setPassword(passwordEncoder.encode(newPassword));
        profile.setPasswordResetToken(null);
        profile.setPasswordResetTokenExpiry(null);
        profileRepository.save(profile);

        log.info("Password successfully reset for user: {}", profile.getEmail());

        // Send confirmation email
        String subject = "Your Money Manager Password Has Been Reset";
        String body = String.format(
            "<html><body>" +
            "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;'>" +
            "<h2 style='color: #333;'>Password Reset Successful</h2>" +
            "<p>Hello %s,</p>" +
            "<p>Your Money Manager password has been successfully reset.</p>" +
            "<p>If you did not make this change, please contact our support team immediately.</p>" +
            "<hr style='border: none; border-top: 1px solid #eee; margin: 30px 0;'>" +
            "<p style='color: #888; font-size: 12px;'>This is an automated message from Money Manager. Please do not reply to this email.</p>" +
            "</div>" +
            "</body></html>",
            profile.getFullname() != null ? profile.getFullname() : "User"
        );

        try {
            emailService.sendEmail(profile.getEmail(), subject, body);
        } catch (Exception e) {
            log.warn("Failed to send password reset confirmation email to: {}", profile.getEmail(), e);
            // Don't fail the reset operation if email fails
        }

        return Map.of(
            "success", true,
            "message", "Password has been reset successfully. You can now log in with your new password."
        );
    }

    /**
     * Validate a password reset token.
     *
     * @param token Password reset token
     * @return Map with validity status
     */
    public Map<String, Object> validateResetToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return Map.of(
                "valid", false,
                "message", "Invalid token"
            );
        }

        ProfileEntity profile = profileRepository.findByPasswordResetToken(token).orElse(null);

        if (profile == null) {
            return Map.of(
                "valid", false,
                "message", "Invalid or expired token"
            );
        }

        if (profile.getPasswordResetTokenExpiry() == null ||
            LocalDateTime.now().isAfter(profile.getPasswordResetTokenExpiry())) {
            return Map.of(
                "valid", false,
                "message", "Token has expired"
            );
        }

        return Map.of(
            "valid", true,
            "message", "Token is valid"
        );
    }
}

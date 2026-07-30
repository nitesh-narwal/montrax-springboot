package in.tracking.moneymanager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "tbl_profiles", indexes = {
        @Index(name = "idx_profiles_email", columnList = "email", unique = true),
        @Index(name = "idx_profiles_created_at", columnList = "created_at"),
        @Index(name = "idx_profiles_is_active", columnList = "is_active"),
        @Index(name = "idx_profiles_role", columnList = "role")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "profile_seq")
    @SequenceGenerator(name = "profile_seq", sequenceName = "seq_profiles", allocationSize = 1)
    private Long id;

    @Column(name = "fullname", length = 100, nullable = false)
    private String fullname;

    @Column(name = "email", length = 100, unique = true, nullable = false)
    private String email;

    // Nullable: OAuth2 users (Google, etc.) have no local password
    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    @Column(name = "created_at", updatable = false, nullable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "activation_token", length = 255)
    private String activationToken;

    // Account deletion fields for 3-day grace period
    @Column(name = "deletion_requested_at")
    private LocalDateTime deletionRequestedAt;

    @Column(name = "deletion_scheduled_at")
    private LocalDateTime deletionScheduledAt;

    @Column(name = "is_pending_deletion", nullable = false)
    private Boolean isPendingDeletion;

    // Password reset fields
    @Column(name = "password_reset_token", length = 255)
    private String passwordResetToken;

    @Column(name = "password_reset_token_expiry")
    private LocalDateTime passwordResetTokenExpiry;

    // Role-based access control
    @Column(name = "role", length = 20, nullable = false)
    private String role;

    // Phone number for OTP verification
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "is_phone_verified", nullable = false)
    private Boolean isPhoneVerified;

    // How this account authenticates: LOCAL (email+password) or an OAuth2 registrationId e.g. GOOGLE
    @Column(name = "auth_provider", length = 20, nullable = false)
    private String authProvider;

    // Preferred local time-of-day for email notifications (budget alerts, bill reminders).
    // Null means "use each job's built-in default time".
    @Column(name = "notification_time")
    private LocalTime notificationTime;

    @PrePersist
    public void prePrePersist() {
        if (this.isActive == null) {
            isActive = false;
        }
        if (this.isPendingDeletion == null) {
            isPendingDeletion = false;
        }
        if (this.role == null) {
            role = "USER";
        }
        if (this.isPhoneVerified == null) {
            isPhoneVerified = false;
        }
        if (this.authProvider == null) {
            authProvider = "LOCAL";
        }
    }
}
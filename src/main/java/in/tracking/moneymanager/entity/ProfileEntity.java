package in.tracking.moneymanager.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_profiles", indexes = {
        @Index(name = "idx_profiles_email", columnList = "email", unique = true),
        @Index(name = "idx_profiles_created_at", columnList = "created_at"),
        @Index(name = "idx_profiles_is_active", columnList = "is_active")
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

    @Column(name = "password", length = 255, nullable = false)
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

    @PrePersist
    public void prePrePersist() {
        if (this.isActive == null) {
            isActive = false;
        }
        if (this.isPendingDeletion == null) {
            isPendingDeletion = false;
        }
    }
}
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
@Table(name = "tbl_profiles")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String fullname;

    @Column(unique = true)
    private String email;
    private String password;
    private String profileImageUrl;

    @Column(updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
    private Boolean isActive;
    private String activationToken;

    // Account deletion fields for 3-day grace period
    @Column(name = "deletion_requested_at")
    private LocalDateTime deletionRequestedAt;

    @Column(name = "deletion_scheduled_at")
    private LocalDateTime deletionScheduledAt;

    @Column(name = "is_pending_deletion")
    private Boolean isPendingDeletion;

    // Password reset fields
    @Column(name = "password_reset_token")
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

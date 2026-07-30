package in.tracking.moneymanager.dto;

import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProfileDTO {
    private Long id;
    private String fullname;
    private String email;
    private String password;
    private String profileImageUrl;
    private String role;
    private String phoneNumber;
    private Boolean isPhoneVerified;
    private String authProvider;
    private LocalTime notificationTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

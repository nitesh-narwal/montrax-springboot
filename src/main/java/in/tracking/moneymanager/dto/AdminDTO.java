package in.tracking.moneymanager.dto;

import lombok.Builder;

import java.time.LocalDateTime;

public class AdminDTO {

    @Builder
    public record UserSummary(
            Long id,
            String fullname,
            String email,
            String role,
            String phoneNumber,
            Boolean isPhoneVerified,
            Boolean isActive,
            LocalDateTime createdAt
    ) {}

    @Builder
    public record SystemStats(
            long totalUsers,
            long activeUsers,
            long adminUsers,
            long phoneVerifiedUsers,
            long totalExpenses,
            long totalIncomes,
            boolean mongoAvailable,
            int cachedConfigCount
    ) {}

    public record RoleUpdateRequest(String role) {}
}

package in.tracking.moneymanager.service;

import in.tracking.moneymanager.document.AppConfigDocument;
import in.tracking.moneymanager.dto.AdminDTO;
import in.tracking.moneymanager.dto.PagedResponse;
import in.tracking.moneymanager.entity.ProfileEntity;
import in.tracking.moneymanager.repository.ExpenceRepository;
import in.tracking.moneymanager.repository.IncomeRepository;
import in.tracking.moneymanager.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private static final Set<String> VALID_ROLES = Set.of("USER", "ADMIN");

    private final ProfileRepository profileRepository;
    private final ExpenceRepository expenceRepository;
    private final IncomeRepository incomeRepository;
    private final AppCacheService appCacheService;

    public PagedResponse<AdminDTO.UserSummary> listUsers(String search, Pageable pageable) {
        var page = (search == null || search.isBlank())
                ? profileRepository.findAll(pageable)
                : profileRepository.findByFullnameContainingIgnoreCaseOrEmailContainingIgnoreCase(search, search, pageable);

        return PagedResponse.of(page, this::toSummary);
    }

    @Transactional
    public AdminDTO.UserSummary updateUserRole(Long userId, String role) {
        String normalizedRole = role == null ? "" : role.trim().toUpperCase();
        if (!VALID_ROLES.contains(normalizedRole)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role. Must be one of: " + VALID_ROLES);
        }

        ProfileEntity profile = profileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String currentAdminEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        if (profile.getEmail().equals(currentAdminEmail) && !normalizedRole.equals("ADMIN")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot demote your own admin account");
        }

        profile.setRole(normalizedRole);
        profile = profileRepository.save(profile);
        log.info("Admin {} changed role of user {} to {}", currentAdminEmail, profile.getEmail(), normalizedRole);
        return toSummary(profile);
    }

    public AdminDTO.SystemStats getSystemStats() {
        return AdminDTO.SystemStats.builder()
                .totalUsers(profileRepository.count())
                .activeUsers(profileRepository.countByIsActiveTrue())
                .adminUsers(profileRepository.countByRole("ADMIN"))
                .phoneVerifiedUsers(profileRepository.countByIsPhoneVerifiedTrue())
                .totalExpenses(expenceRepository.count())
                .totalIncomes(incomeRepository.count())
                .mongoAvailable(appCacheService.isMongoAvailable())
                .cachedConfigCount(appCacheService.getAllAsMap().size())
                .build();
    }

    public void refreshCache() {
        String currentAdminEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        log.info("Admin {} triggered manual cache refresh", currentAdminEmail);
        appCacheService.forceRefresh();
    }

    public List<AppConfigDocument> getAllConfig() {
        return appCacheService.getAll();
    }

    public List<AppConfigDocument> getConfigByCategory(String category) {
        return appCacheService.getAllByCategory(category);
    }

    @Transactional
    public void updateConfig(String key, String value, String category, String description, boolean isSecret) {
        String currentAdminEmail = SecurityContextHolder.getContext().getAuthentication().getName();
        appCacheService.set(key, value, category, description, isSecret, currentAdminEmail);
    }

    private AdminDTO.UserSummary toSummary(ProfileEntity profile) {
        return AdminDTO.UserSummary.builder()
                .id(profile.getId())
                .fullname(profile.getFullname())
                .email(profile.getEmail())
                .role(profile.getRole())
                .phoneNumber(profile.getPhoneNumber())
                .isPhoneVerified(profile.getIsPhoneVerified())
                .isActive(profile.getIsActive())
                .createdAt(profile.getCreatedAt())
                .build();
    }
}

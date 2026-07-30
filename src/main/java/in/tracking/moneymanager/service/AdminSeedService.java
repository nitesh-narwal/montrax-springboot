package in.tracking.moneymanager.service;

import in.tracking.moneymanager.entity.ProfileEntity;
import in.tracking.moneymanager.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the first admin account from ADMIN_EMAIL / ADMIN_PASSWORD env vars on first run.
 * No-ops if an admin already exists or the env vars aren't set, so it's safe on every restart.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class AdminSeedService implements CommandLineRunner {

    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppCacheService appCacheService;

    @Override
    public void run(String... args) {
        if (profileRepository.countByRole("ADMIN") > 0) {
            log.info("Admin account already exists. Skipping admin seed.");
            return;
        }

        String adminEmail = appCacheService.get("admin.email");
        String adminPassword = appCacheService.get("admin.password");

        if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
            log.info("ADMIN_EMAIL / ADMIN_PASSWORD not configured. Skipping admin seed.");
            return;
        }

        profileRepository.findByEmail(adminEmail).ifPresentOrElse(
                existing -> {
                    existing.setRole("ADMIN");
                    profileRepository.save(existing);
                    log.info("Promoted existing user {} to ADMIN", adminEmail);
                },
                () -> {
                    ProfileEntity admin = ProfileEntity.builder()
                            .fullname("Administrator")
                            .email(adminEmail)
                            .password(passwordEncoder.encode(adminPassword))
                            .role("ADMIN")
                            .isActive(true)
                            .isPendingDeletion(false)
                            .isPhoneVerified(false)
                            .build();
                    profileRepository.save(admin);
                    log.info("Seeded admin account: {}", adminEmail);
                }
        );
    }
}

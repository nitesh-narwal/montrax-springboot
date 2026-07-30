package in.tracking.moneymanager.controller;

import in.tracking.moneymanager.annotation.AdminOnly;
import in.tracking.moneymanager.document.AppConfigDocument;
import in.tracking.moneymanager.dto.AdminDTO;
import in.tracking.moneymanager.dto.PagedResponse;
import in.tracking.moneymanager.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin-only endpoints for user management, system stats, and AppCache control.
 * Access enforced twice: SecurityConfig matcher (hasRole("ADMIN")) and @AdminOnly aspect.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    @AdminOnly
    public ResponseEntity<PagedResponse<AdminDTO.UserSummary>> listUsers(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(adminService.listUsers(search, pageable));
    }

    @PutMapping("/users/{userId}/role")
    @AdminOnly
    public ResponseEntity<AdminDTO.UserSummary> updateUserRole(
            @PathVariable Long userId,
            @RequestBody AdminDTO.RoleUpdateRequest request) {
        return ResponseEntity.ok(adminService.updateUserRole(userId, request.role()));
    }

    @GetMapping("/stats")
    @AdminOnly
    public ResponseEntity<AdminDTO.SystemStats> getStats() {
        return ResponseEntity.ok(adminService.getSystemStats());
    }

    @PostMapping("/cache/refresh")
    @AdminOnly
    public ResponseEntity<Map<String, String>> refreshCache() {
        adminService.refreshCache();
        return ResponseEntity.ok(Map.of("message", "AppCache refresh triggered"));
    }

    @GetMapping("/config")
    @AdminOnly
    public ResponseEntity<List<AppConfigDocument>> getAllConfig(@RequestParam(required = false) String category) {
        List<AppConfigDocument> config = category != null
                ? adminService.getConfigByCategory(category)
                : adminService.getAllConfig();
        return ResponseEntity.ok(config);
    }

    @PutMapping("/config/{key}")
    @AdminOnly
    public ResponseEntity<Map<String, String>> updateConfig(
            @PathVariable String key,
            @RequestBody Map<String, Object> body) {
        String value = (String) body.get("value");
        String category = (String) body.get("category");
        String description = (String) body.get("description");
        boolean isSecret = Boolean.TRUE.equals(body.get("isSecret"));
        adminService.updateConfig(key, value, category, description, isSecret);
        return ResponseEntity.ok(Map.of("message", "Config updated: " + key));
    }
}

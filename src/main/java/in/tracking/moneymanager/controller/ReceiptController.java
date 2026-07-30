package in.tracking.moneymanager.controller;

import in.tracking.moneymanager.entity.ProfileEntity;
import in.tracking.moneymanager.service.CloudinaryService;
import in.tracking.moneymanager.service.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Upload a receipt/proof-of-payment image or PDF for attaching to an
 * expense or income at creation time.
 */
@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
@Slf4j
public class ReceiptController {

    private final CloudinaryService cloudinaryService;
    private final ProfileService profileService;

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadReceipt(@RequestParam("file") MultipartFile file) {
        ProfileEntity profile = profileService.getCurrentProfile();
        try {
            String url = cloudinaryService.uploadReceipt(file, profile.getId());
            return ResponseEntity.ok(Map.of("success", true, "url", url));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Receipt upload failed for profile {}: {}", profile.getId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("success", false, "message", "Failed to upload receipt"));
        }
    }
}

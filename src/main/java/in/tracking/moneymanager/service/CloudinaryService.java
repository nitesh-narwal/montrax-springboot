package in.tracking.moneymanager.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;

    /**
     * Upload a profile image to Cloudinary.
     * Automatically deletes the old image before uploading the new one.
     * Images are stored in the 'profile-images' folder with transformations applied.
     *
     * @param file The image file to upload
     * @param userId The user's ID (used for naming)
     * @return The secure URL of the uploaded image
     * @throws IOException If upload fails
     */
    public String uploadProfileImage(MultipartFile file, Long userId) throws IOException {
        validateFile(file);

        // Delete the old image first to keep Cloudinary clean
        // This ensures no orphaned images if public_id format changes
        deleteProfileImage(userId);

        try {
            // Upload with eager transformations for profile images
            @SuppressWarnings("unchecked")
            Map<String, Object> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", "money-manager/profile-images",
                    "public_id", "user_" + userId,
                    "overwrite", true,
                    "invalidate", true,  // Invalidate CDN cache for immediate update
                    "resource_type", "image"
            ));

            // Get the secure URL - return directly without transformations
            // Cloudinary applies transformations during upload, or we use their CDN
            String secureUrl = (String) uploadResult.get("secure_url");

            log.info("Profile image uploaded successfully for user {}: {}", userId, secureUrl);
            return secureUrl;

        } catch (IOException e) {
            log.error("Failed to upload profile image for user {}: {}", userId, e.getMessage());
            throw new IOException("Failed to upload image to Cloudinary: " + e.getMessage(), e);
        }
    }

    /**
     * Delete a profile image from Cloudinary.
     * Called automatically before uploading a new image, or manually when user removes their profile picture.
     *
     * @param userId The user's ID
     * @return true if deletion was successful or image didn't exist, false if deletion failed
     */
    public boolean deleteProfileImage(Long userId) {
        try {
            String publicId = "money-manager/profile-images/user_" + userId;
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().destroy(publicId, ObjectUtils.asMap(
                    "invalidate", true  // Invalidate CDN cache
            ));

            String deleteResult = (String) result.get("result");
            if ("ok".equals(deleteResult)) {
                log.info("Profile image deleted for user {}", userId);
                return true;
            } else if ("not found".equals(deleteResult)) {
                log.debug("No existing profile image found for user {}", userId);
                return true; // Not an error - image just didn't exist
            } else {
                log.warn("Unexpected result when deleting profile image for user {}: {}", userId, deleteResult);
                return false;
            }
        } catch (IOException e) {
            log.warn("Failed to delete profile image for user {}: {}", userId, e.getMessage());
            // Don't throw - deletion failure shouldn't break the upload flow
            return false;
        }
    }

    /**
     * Check if a profile image exists for a user.
     *
     * @param userId The user's ID
     * @return true if image exists, false otherwise
     */
    public boolean profileImageExists(Long userId) {
        try {
            String publicId = "money-manager/profile-images/user_" + userId;
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.api().resource(publicId, ObjectUtils.emptyMap());
            return result != null && result.containsKey("public_id");
        } catch (Exception e) {
            // Resource not found or other error
            return false;
        }
    }

    /**
     * Validate the uploaded file.
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty or not provided");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are allowed");
        }

        // Max 5MB
        long maxSize = 5 * 1024 * 1024;
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("File size must be less than 5MB");
        }

        // Check allowed types
        String[] allowedTypes = {"image/jpeg", "image/png", "image/gif", "image/webp"};
        boolean isAllowed = false;
        for (String type : allowedTypes) {
            if (type.equals(contentType)) {
                isAllowed = true;
                break;
            }
        }
        if (!isAllowed) {
            throw new IllegalArgumentException("Only JPEG, PNG, GIF, and WebP images are allowed");
        }
    }
}


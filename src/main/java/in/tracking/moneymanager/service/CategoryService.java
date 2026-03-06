package in.tracking.moneymanager.service;

import in.tracking.moneymanager.dto.CategoryDTO;
import in.tracking.moneymanager.entity.CategoryEntity;
import in.tracking.moneymanager.entity.ProfileEntity;
import in.tracking.moneymanager.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProfileService profileService;

    //save category
    public CategoryDTO saveCategory(CategoryDTO categoryDTO) {
        ProfileEntity profile = profileService.getCurrentProfile();
        if (categoryRepository.existsByNameAndProfileId(categoryDTO.getName(), profile.getId()))
            throw new RuntimeException("Category with this name already exists");
        CategoryEntity newCategory = toEntity(categoryDTO, profile);
        return toDTO(categoryRepository.save(newCategory));
    }

    //get categories for current users
    public List<CategoryDTO> getCategoriesForCurrentUser() {
        ProfileEntity profile = profileService.getCurrentProfile();
        return categoryRepository.findByProfileId(profile.getId())
                .stream().map(this::toDTO).toList();
    }

    //get categories by type for current user
    public List<CategoryDTO> getCategoriesByTypeForCurrentUser(String type) {
        ProfileEntity profile = profileService.getCurrentProfile();
        return categoryRepository.findByTypeAndProfileId(type, profile.getId())
                .stream().map(this::toDTO).toList();
    }

    //update category
    public CategoryDTO updateCategory(Long categoryId, CategoryDTO categoryDTO) {
        CategoryEntity existingCategory = categoryRepository.findByIdAndProfileId(
                        categoryId, profileService.getCurrentProfile().getId())
                .orElseThrow(() -> new RuntimeException("Category not found or you don't have permission to update it"));
        existingCategory.setName(categoryDTO.getName());
        existingCategory.setIcon(categoryDTO.getIcon());
        existingCategory.setType(categoryDTO.getType());
        existingCategory = categoryRepository.save(existingCategory);
        return toDTO(existingCategory);
    }

    //delete category
    public void deleteCategory(Long categoryId) {
        CategoryEntity existingCategory = categoryRepository.findByIdAndProfileId(
                        categoryId, profileService.getCurrentProfile().getId())
                .orElseThrow(() -> new RuntimeException("Category not found or you don't have permission to delete it"));
        categoryRepository.delete(existingCategory);
    }

    //helper methods
    private CategoryEntity toEntity(CategoryDTO categoryDTO, ProfileEntity profile) {
        return CategoryEntity.builder()
                .name(categoryDTO.getName())
                .icon(categoryDTO.getIcon())
                .profile(profile)
                .type(categoryDTO.getType())
                .build();
    }

    private CategoryDTO toDTO(CategoryEntity entity) {
        return CategoryDTO.builder()
                .id(entity.getId())
                .profileId(entity.getProfile() != null ? entity.getProfile().getId() : null)
                .name(entity.getName())
                .icon(entity.getIcon())
                .type(entity.getType())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

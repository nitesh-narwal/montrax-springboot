package in.tracking.moneymanager.service;

import in.tracking.moneymanager.dto.ExpenceDTO;
import in.tracking.moneymanager.entity.CategoryEntity;
import in.tracking.moneymanager.entity.ExpenceEntity;
import in.tracking.moneymanager.entity.ProfileEntity;
import in.tracking.moneymanager.repository.CategoryRepository;
import in.tracking.moneymanager.repository.ExpenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpenceService {

    private final CategoryRepository categoryRepository;
    private final ExpenceRepository expenceRepository;
    private final ProfileService profileService;

    @Transactional
    public ExpenceDTO addExpence(ExpenceDTO dto) {
        ProfileEntity profile = profileService.getCurrentProfile();
        CategoryEntity category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + dto.getCategoryId()));
        ExpenceEntity newExpence = toEntity(dto, profile, category);
        ExpenceEntity savedExpence = expenceRepository.save(newExpence);
        return toDTO(savedExpence);
    }

    //Retrieves all expences for current month/based on the start date or end date
    public List<ExpenceDTO> getCurrentMonthExpenceForCurrentUser() {
        ProfileEntity profile = profileService.getCurrentProfile();
        LocalDate now = LocalDate.now();
        return expenceRepository.findByProfileIdAndDateBetween(profile.getId(), now.withDayOfMonth(1), now.withDayOfMonth(now.lengthOfMonth()))
                .stream().map(this::toDTO).toList();
    }

    //delete expence by id for current user
    @Transactional
    public void deleteExpence(long expenceId){
        ProfileEntity profile = profileService.getCurrentProfile();
        ExpenceEntity entity = expenceRepository.findById(expenceId)
                .orElseThrow(() -> new RuntimeException("Expence not found with id: " + expenceId));
        if(!entity.getProfile().getId().equals(profile.getId())) {
            throw new RuntimeException("You don't have permission to delete this expence");
        }
        expenceRepository.deleteById(expenceId);
    }

    //get latest 5 expence for current user
    public List<ExpenceDTO> getLatest5ExpenceForCurrentUser() {
        ProfileEntity profile = profileService.getCurrentProfile();
        return expenceRepository.findTop5ByProfileIdOrderByDateDesc(profile.getId())
                .stream().map(this::toDTO).toList();
    }

    //get total expances for current user
    public BigDecimal getTotalExpenceForCurrentUser() {
        ProfileEntity profile = profileService.getCurrentProfile();
        BigDecimal total = expenceRepository.findTotalExpenceByProfileId(profile.getId());
        return total != null ? total : BigDecimal.ZERO;
    }

    //filter expences
    public List<ExpenceDTO> filterExpences(LocalDate startDate, LocalDate endDate, String keyword, Sort sort){
        List<ExpenceEntity> list = expenceRepository.findByProfileIdAndDateBetweenAndNameContainingIgnoreCase(
                profileService.getCurrentProfile().getId(), startDate, endDate, keyword, sort);
        return list.stream().map(this::toDTO).toList();
    }

    //Notificationa
    public List<ExpenceDTO> getExpenceForUserOnDate(Long profileId, LocalDate date){
        List<ExpenceEntity> list = expenceRepository.findByProfileIdAndDate(profileId, date);
        return list.stream().map(this::toDTO).toList();
    }

    /**
     * Add expense for a specific profile (used by recurring transactions).
     * Does not require authentication context.
     */
    @Transactional
    public ExpenceDTO addExpenceForProfile(ExpenceDTO dto, Long profileId) {
        ProfileEntity profile = profileService.getProfileById(profileId);
        CategoryEntity category = null;
        if (dto.getCategoryId() != null) {
            category = categoryRepository.findById(dto.getCategoryId()).orElse(null);
        }
        ExpenceEntity newExpence = ExpenceEntity.builder()
                .name(dto.getName())
                .icon(dto.getIcon())
                .amount(dto.getAmount())
                .date(dto.getDate())
                .profile(profile)
                .category(category)
                .build();
        ExpenceEntity savedExpence = expenceRepository.save(newExpence);
        return toDTO(savedExpence);
    }


    //helper method to calculate total expence for a given category
    private ExpenceEntity toEntity(ExpenceDTO expenceDTO, ProfileEntity profile, CategoryEntity category) {
        return ExpenceEntity.builder()
                .name(expenceDTO.getName())
                .icon(expenceDTO.getIcon())
                .amount(expenceDTO.getAmount())
                .date(expenceDTO.getDate())
                .profile(profile)
                .category(category)
                .build();
    }

    private ExpenceDTO toDTO(ExpenceEntity entity) {
        return ExpenceDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .icon(entity.getIcon())
                .categoryId(entity.getCategory() != null ? entity.getCategory().getId() : null)
                .categoryName(entity.getCategory() != null ? entity.getCategory().getName() : "N/A")
                .amount(entity.getAmount())
                .date(entity.getDate())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

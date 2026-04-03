package in.tracking.moneymanager.service;

import in.tracking.moneymanager.dto.IncomeDTO;
import in.tracking.moneymanager.entity.CategoryEntity;
import in.tracking.moneymanager.entity.IncomeEntity;
import in.tracking.moneymanager.entity.ProfileEntity;
import in.tracking.moneymanager.repository.CategoryRepository;
import in.tracking.moneymanager.repository.IncomeRepository;
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
public class IncomeService {

    private final CategoryRepository categoryRepository;
    private final IncomeRepository incomeRepository;
    private final ProfileService profileService;


    @Transactional
    public IncomeDTO addIncome(IncomeDTO dto) {
        ProfileEntity profile = profileService.getCurrentProfile();
        CategoryEntity category = categoryRepository.findById(dto.getCategoryId())
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + dto.getCategoryId()));
        IncomeEntity newIncome = toEntity(dto, profile, category);
        IncomeEntity savedIncome = incomeRepository.save(newIncome);
        return toDTO(savedIncome);
    }

    //Retrieves all income for current month/based on the start date or end date
    public List<IncomeDTO> getCurrentMonthIncomeForCurrentUser() {
        ProfileEntity profile = profileService.getCurrentProfile();
        LocalDate now = LocalDate.now();
        return incomeRepository.findByProfileIdAndDateBetween(profile.getId(), now.withDayOfMonth(1), now.withDayOfMonth(now.lengthOfMonth()))
                .stream().map(this::toDTO).toList();
    }

    //delete income by id for current user
    @Transactional
    public void deleteIncome(long incomeId){
        ProfileEntity profile = profileService.getCurrentProfile();
        IncomeEntity entity = incomeRepository.findById(incomeId)
                .orElseThrow(() -> new RuntimeException("Income not found with id: " + incomeId));
        if(!entity.getProfile().getId().equals(profile.getId())) {
            throw new RuntimeException("You don't have permission to delete this income");
        }
        incomeRepository.deleteById(incomeId);
    }

    //get latest 5 income for current user
    public List<IncomeDTO> getLatest5IncomeForCurrentUser() {
        ProfileEntity profile = profileService.getCurrentProfile();
        return incomeRepository.findTop5ByProfileIdOrderByDateDesc(profile.getId())
                .stream().map(this::toDTO).toList();
    }

    //get total income for current user
    public BigDecimal getTotalIncomeForCurrentUser() {
        ProfileEntity profile = profileService.getCurrentProfile();
        BigDecimal total = incomeRepository.findTotalIncomeByProfileId(profile.getId());
        return total != null ? total : BigDecimal.ZERO;
    }

    //filter incomes
    public List<IncomeDTO> filterIncomes(LocalDate startDate, LocalDate endDate, String keyword, Sort sort){
        List<IncomeEntity> list = incomeRepository.findByProfileIdAndDateBetweenAndNameContainingIgnoreCase(
                profileService.getCurrentProfile().getId(), startDate, endDate, keyword, sort);
        return list.stream().map(this::toDTO).toList();
    }

    public BigDecimal getTotalIncomeForDateRangeForCurrentUser(LocalDate startDate, LocalDate endDate) {
        ProfileEntity profile = profileService.getCurrentProfile();
        BigDecimal total = incomeRepository.findTotalIncomeByProfileIdAndDateBetween(profile.getId(), startDate, endDate);
        return total != null ? total : BigDecimal.ZERO;
    }


    /**
     * Add income for a specific profile (used by recurring transactions).
     * Does not require authentication context.
     */
    @Transactional
    public IncomeDTO addIncomeForProfile(IncomeDTO dto, Long profileId) {
        ProfileEntity profile = profileService.getProfileById(profileId);
        CategoryEntity category = null;
        if (dto.getCategoryId() != null) {
            category = categoryRepository.findById(dto.getCategoryId()).orElse(null);
        }
        IncomeEntity newIncome = IncomeEntity.builder()
                .name(dto.getName())
                .icon(dto.getIcon())
                .amount(dto.getAmount())
                .date(dto.getDate())
                .profile(profile)
                .category(category)
                .build();
        IncomeEntity savedIncome = incomeRepository.save(newIncome);
        return toDTO(savedIncome);
    }

    //helper method to calculate total expence for a given category
    private IncomeEntity toEntity(IncomeDTO incomeDTO, ProfileEntity profile, CategoryEntity category){
        return IncomeEntity.builder()
                .name(incomeDTO.getName())
                .icon(incomeDTO.getIcon())
                .amount(incomeDTO.getAmount())
                .date(incomeDTO.getDate())
                .profile(profile)
                .category(category)
                .build();
    }

    private IncomeDTO toDTO(IncomeEntity entity) {
        return IncomeDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .icon(entity.getIcon())
                .categoryId(entity.getCategory() != null ? entity.getCategory().getId(): null)
                .categoryName(entity.getCategory() != null ? entity.getCategory().getName(): "N/A")
                .amount(entity.getAmount())
                .date(entity.getDate())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

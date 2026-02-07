package in.tracking.moneymanager.service;

import in.tracking.moneymanager.dto.AuthDTO;
import in.tracking.moneymanager.dto.ProfileDTO;
import in.tracking.moneymanager.entity.ProfileEntity;
import in.tracking.moneymanager.repository.ProfileRepository;
import in.tracking.moneymanager.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.file.ProviderNotFoundException;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final EmailService emailService;
    private final ProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public ProfileDTO registerProfile(ProfileDTO profileDTO) {
        ProfileEntity newProfile = toEntity(profileDTO);
        newProfile.setActivationToken(UUID.randomUUID().toString());
        newProfile = profileRepository.save(newProfile);

        //send activation email
        String activationLink = "http://localhost:8090/activate/" + newProfile.getActivationToken();
        String subject = "Activate your Money Manager account";
        String body = "Please click on the following link to activate your account: " + activationLink;
        emailService.sendEmail(newProfile.getEmail(), subject, body);
        return toDTO(newProfile);
    }

    public ProfileEntity toEntity(ProfileDTO profileDTO){
        return ProfileEntity.builder()
                .id(profileDTO.getId())
                .fullname(profileDTO.getFullname())
                .email(profileDTO.getEmail())
                .password(passwordEncoder.encode(profileDTO.getPassword()))
                .profileImageUrl(profileDTO.getProfileImageUrl())
                .createdAt(profileDTO.getCreatedAt())
                .updatedAt(profileDTO.getUpdatedAt())
                .build();
    }

    public ProfileDTO toDTO(ProfileEntity profileEntity){
        return ProfileDTO.builder()
                .id(profileEntity.getId())
                .fullname(profileEntity.getFullname())
                .email(profileEntity.getEmail())
                .profileImageUrl(profileEntity.getProfileImageUrl())
                .createdAt(profileEntity.getCreatedAt())
                .updatedAt(profileEntity.getUpdatedAt())
                .build();
    }

    public boolean activateProfile(String activationToken){
        return profileRepository.findByActivationToken(activationToken)
                .map(profile -> {;
                    profile.setIsActive(true);
                    profileRepository.save(profile);
                    return true;
                })
                .orElse(false);
    }

    public boolean isAccountActive(String email){
        return profileRepository.findByEmail(email)
                .map(ProfileEntity::getIsActive)
                .orElse(false);
    }

    public ProfileEntity getCurrentProfile(String email){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return profileRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ProviderNotFoundException("Profile not found with email" + authentication.getName()));
    }

    public ProfileDTO getPublicProfile(String email){
        ProfileEntity currentUser = null;
        if(email == null){
            currentUser = getCurrentProfile(email);
        }else{
            currentUser = profileRepository.findByEmail(email)
                    .orElseThrow(() -> new ProviderNotFoundException("Profile not found with email" + email));
        }

        return ProfileDTO.builder()
                .id(currentUser.getId())
                .fullname(currentUser.getFullname())
                .email(currentUser.getEmail())
                .profileImageUrl(currentUser.getProfileImageUrl())
                .createdAt(currentUser.getCreatedAt())
                .updatedAt(currentUser.getUpdatedAt())
                .build();
    }

    public Map<String, Object> authenticateAndGenerateToken(AuthDTO authDTO) {
        try{
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(authDTO.getEmail(), authDTO.getPassword()));
                //Grnerate JWT token
            String token = jwtUtil.generateToken(authDTO.getEmail());
                return Map.of("token", token,
                        "user", getPublicProfile(authDTO.getEmail()));

        }catch (Exception e){
            throw new RuntimeException("Invalid username/password supplied");
        }
    }
}

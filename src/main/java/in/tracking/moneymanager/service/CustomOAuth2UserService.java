package in.tracking.moneymanager.service;

import in.tracking.moneymanager.entity.ProfileEntity;
import in.tracking.moneymanager.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs on every Google login. Finds the matching profile by email or creates
 * one on first login (auto-linking to any existing local account with the
 * same email, since the provider already verified it).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final ProfileRepository profileRepository;

    @Override
    @Transactional
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String picture = oAuth2User.getAttribute("picture");
        String registrationId = userRequest.getClientRegistration().getRegistrationId().toUpperCase();

        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException("Email not provided by " + registrationId);
        }

        profileRepository.findByEmail(email).ifPresentOrElse(existing -> {
            if (existing.getProfileImageUrl() == null && picture != null) {
                existing.setProfileImageUrl(picture);
                profileRepository.save(existing);
            }
        }, () -> {
            ProfileEntity newProfile = ProfileEntity.builder()
                    .fullname(name != null ? name : email)
                    .email(email)
                    .profileImageUrl(picture)
                    .isActive(true)
                    .isPhoneVerified(true) // Google already verified the user's identity
                    .isPendingDeletion(false)
                    .role("USER")
                    .authProvider(registrationId)
                    .build();
            profileRepository.save(newProfile);
            log.info("Created new profile via {} OAuth2 login: {}", registrationId, email);
        });

        return oAuth2User;
    }
}

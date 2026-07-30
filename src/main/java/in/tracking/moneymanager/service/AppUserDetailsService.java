package in.tracking.moneymanager.service;

import in.tracking.moneymanager.entity.ProfileEntity;
import in.tracking.moneymanager.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {

    private final ProfileRepository profileRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {

        ProfileEntity existingProfile = profileRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email : " + email));

        String role = existingProfile.getRole() != null ? existingProfile.getRole() : "USER";
        // OAuth2 accounts (Google, etc.) have no local password; JWT auth never checks it
        String password = existingProfile.getPassword() != null ? existingProfile.getPassword() : "{noop}oauth2-account";

        return User.builder()
                .username(existingProfile.getEmail())
                .password(password)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + role)))
                .build();
    }
}

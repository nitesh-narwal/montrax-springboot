package in.tracking.moneymanager.security;

import in.tracking.moneymanager.entity.ProfileEntity;
import in.tracking.moneymanager.repository.ProfileRepository;
import in.tracking.moneymanager.service.AppCacheService;
import in.tracking.moneymanager.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Runs after Google finishes authenticating the user. Issues the same kind
 * of JWT the normal /login endpoint would, but never puts it in the redirect
 * URL - a long-lived bearer token in a URL ends up in access logs, browser
 * history, and any analytics that captures location.href. Instead a random,
 * single-use exchange code is put in the URL; the frontend immediately
 * swaps it for the real token via a POST body (see ProfileController#exchangeOAuth2Code).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final String EXCHANGE_KEY_PREFIX = "oauth2_exchange:";
    private static final Duration EXCHANGE_CODE_TTL = Duration.ofSeconds(60);

    private final JwtUtil jwtUtil;
    private final ProfileRepository profileRepository;
    private final AppCacheService appCacheService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");

        ProfileEntity profile = profileRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("OAuth2 profile not found for " + email));

        String role = profile.getRole() != null ? profile.getRole() : "USER";
        String token = jwtUtil.generateToken(email, Map.of("role", role));

        String code = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(EXCHANGE_KEY_PREFIX + code, token, EXCHANGE_CODE_TTL);

        String frontendUrl = appCacheService.get("money.manager.frontend.url", "http://localhost:5173");
        String redirectUrl = frontendUrl + "/oauth2/redirect?code=" + URLEncoder.encode(code, StandardCharsets.UTF_8);

        log.info("OAuth2 login success for {}, redirecting to frontend", email);
        response.sendRedirect(redirectUrl);
    }
}

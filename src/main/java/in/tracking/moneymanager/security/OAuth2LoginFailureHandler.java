package in.tracking.moneymanager.security;

import in.tracking.moneymanager.service.AppCacheService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private final AppCacheService appCacheService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException {
        log.warn("OAuth2 login failed: {}", exception.getMessage());
        String frontendUrl = appCacheService.get("money.manager.frontend.url", "http://localhost:5173");
        String redirectUrl = frontendUrl + "/login?error=" +
                URLEncoder.encode("Google sign-in failed. Please try again.", StandardCharsets.UTF_8);
        response.sendRedirect(redirectUrl);
    }
}

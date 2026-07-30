package in.tracking.moneymanager.config;

import in.tracking.moneymanager.security.AuditFilter;
import in.tracking.moneymanager.security.IdempotencyFilter;
import in.tracking.moneymanager.security.JwtRequestFilter;
import in.tracking.moneymanager.security.OAuth2LoginFailureHandler;
import in.tracking.moneymanager.security.OAuth2LoginSuccessHandler;
import in.tracking.moneymanager.security.RateLimitFilter;
import in.tracking.moneymanager.service.AppCacheService;
import in.tracking.moneymanager.service.AppUserDetailsService;
import in.tracking.moneymanager.service.CustomOAuth2UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final AppUserDetailsService appUserDetailsService;
    private final AuthenticationConfiguration authenticationConfiguration;
    private final JwtRequestFilter jwtRequestFilter;
    private final RateLimitFilter rateLimitFilter;
    private final IdempotencyFilter idempotencyFilter;
    private final AuditFilter auditFilter;
    private final AppCacheService appCacheService;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final OAuth2LoginFailureHandler oAuth2LoginFailureHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
                httpSecurity.cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        // Public endpoints - no authentication required
                        .requestMatchers("/register", "/activate/**", "/resend-activation", "/status", "/health", "/login").permitAll()
                        // Actuator endpoints for health checks
                        .requestMatchers("/actuator/**").permitAll()
                        // Password reset endpoints - public
                        .requestMatchers("/forgot-password", "/reset-password", "/validate-reset-token").permitAll()
                        // Subscription plans - public for pricing page
                        .requestMatchers("/api/subscription/plans").permitAll()
                        // OTP endpoints - public, used during registration before login
                        .requestMatchers("/api/otp/**").permitAll()
                        // OAuth2 login (Google) - public, browser redirect flow
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        // Admin panel - ADMIN role required
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // All other requests require authentication
                        .anyRequest().authenticated())
                        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                        .oauth2Login(oauth2 -> oauth2
                                .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                                .successHandler(oAuth2LoginSuccessHandler)
                                .failureHandler(oAuth2LoginFailureHandler))
                        .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class)
                        .addFilterAfter(rateLimitFilter, JwtRequestFilter.class)
                        .addFilterAfter(idempotencyFilter, RateLimitFilter.class)
                        .addFilterAfter(auditFilter, IdempotencyFilter.class);
        return httpSecurity.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfiguration = new CorsConfiguration();
        // Allow frontend origins
        String frontendUrl = appCacheService.get("money.manager.frontend.url", "http://localhost:5173");
        corsConfiguration.setAllowedOriginPatterns(List.of(
                frontendUrl,
                "http://localhost:5173",
                "http://localhost:3000",
                "http://localhost:8080",
                "http://127.0.0.1:5173",
                "https://*.vercel.app",
                "*"
        ));
        corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        corsConfiguration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With", "X-Idempotency-Key"));
        corsConfiguration.setExposedHeaders(List.of("Authorization"));
        corsConfiguration.setAllowCredentials(true);
        corsConfiguration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfiguration);
        return source;
    }

    @Bean
    public AuthenticationManager authenticationManager() throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

}

package com.workpulse.config;

import com.workpulse.security.CsrfCookieFilter;
import com.workpulse.security.SessionRevocationFilter;
import com.workpulse.security.SessionRevocationService;
import com.workpulse.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Session-based authentication, CSRF protection (cookie + X-XSRF-TOKEN header), role-based access,
 * security headers and JSON 401/403 responses.
 *
 * Authorization is layered: coarse URL rules here + an explicit @PreAuthorize on every controller
 * method + resource-ownership checks inside the services.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final SessionRevocationService sessionRevocationService;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler,
                          SessionRevocationService sessionRevocationService) {
        this.userDetailsService = userDetailsService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.sessionRevocationService = sessionRevocationService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);

        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(requestHandler)
                // Only the unauthenticated entry points and static files skip CSRF checks.
                .ignoringRequestMatchers("/api/auth/login", "/api/auth/register",
                        "/", "/*.html", "/assets/**", "/favicon.ico")
            )
            .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
            .addFilterAfter(new SessionRevocationFilter(sessionRevocationService), SecurityContextHolderFilter.class)
            .securityContext(context -> context.securityContextRepository(securityContextRepository()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(sf -> sf.changeSessionId())
            )
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data:; font-src 'self'; connect-src 'self'; "
                    + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'"))
                .frameOptions(frame -> frame.deny())
                .referrerPolicy(ref -> ref.policy(ReferrerPolicy.NO_REFERRER))
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/*.html", "/assets/**", "/favicon.ico").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
                .requestMatchers("/api/auth/register", "/api/auth/login", "/api/auth/config").permitAll()
                .requestMatchers("/api/admin/**", "/api/audit-logs/**").hasRole("ADMIN")
                .requestMatchers("/api/reports/**").hasAnyRole("ADMIN", "MANAGER")
                .requestMatchers("/api/**").authenticated()
                .anyRequest().denyAll()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authenticationProvider(authenticationProvider());

        return http.build();
    }
}

package sk.drabikp.bzscraper.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Who may use the app: one account ({@link AuthProperties}), signed in through the API
 * ({@code /api/auth/login}) into a session cookie, optionally remembered for
 * {@code remember-me-days}. Everything under {@code /api} needs it (401 otherwise, never a
 * redirect — the page shows its own sign-in); the page itself and its files are public.
 * CSRF protection as for a single-page app: the token travels in the {@code XSRF-TOKEN} cookie
 * and comes back in the {@code X-XSRF-TOKEN} header.
 */
@Configuration
class SecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService users(AuthProperties auth) {
        String password = auth.password();
        if (password.isBlank()) {
            byte[] random = new byte[12];
            new SecureRandom().nextBytes(random);
            password = HexFormat.of().formatHex(random);
            log.warn("No bzscraper.auth.password set — sign in as '{}' with the generated password: {}",
                    auth.username(), password);
        }
        // stable across restarts: the "remember me" cookie is signed with it (a fresh bcrypt salt on every
        // start would sign everyone out); a configured {bcrypt}… value is used as it is
        String stored = password.startsWith("{") ? password : "{noop}" + password;
        return new InMemoryUserDetailsManager(User.withUsername(auth.username()).password(stored).roles("ADMIN")
                .build());
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    TokenBasedRememberMeServices rememberMeServices(AuthProperties auth, UserDetailsService users) {
        TokenBasedRememberMeServices services = new TokenBasedRememberMeServices(rememberMeKey(auth), users);
        services.setAlwaysRemember(true);                 // the login asks for it only when the user ticked it
        services.setTokenValiditySeconds(auth.rememberMeDays() * 24 * 3600);
        services.setCookieName("bzscraper-remember");
        return services;
    }

    @Bean
    SecurityFilterChain api(HttpSecurity http, SecurityContextRepository contexts,
                            TokenBasedRememberMeServices rememberMe) throws Exception {
        http.authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/auth/**", "/actuator/health").permitAll()
                        .requestMatchers("/api/**", "/actuator/**").authenticated()
                        .anyRequest().permitAll())
                .securityContext(context -> context.securityContextRepository(contexts))
                .csrf(csrf -> csrf.spa())
                .rememberMe(remember -> remember.rememberMeServices(rememberMe))
                .logout(logout -> logout.logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());
        return http.build();
    }

    private static String rememberMeKey(AuthProperties auth) {
        if (!auth.rememberMeKey().isBlank()) {
            return auth.rememberMeKey();
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((auth.username() + "\n" + auth.password()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

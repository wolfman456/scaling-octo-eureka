package com.wood.worker.config;

import com.wood.worker.repository.AdminUserRepository;
import com.wood.worker.security.AdminLoginThrottleFilter;
import com.wood.worker.security.LoginAttemptLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

import java.time.Clock;

@Configuration
public class SecurityConfig {

    /**
     * The H2 console is a development tool. It is reachable without credentials only
     * outside the production profile, and in production the main chain leaves it
     * behind {@code anyRequest().authenticated()} as a second line of defence.
     */
    @Bean
    @Order(1)
    @Profile("!production")
    SecurityFilterChain h2ConsoleSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/h2-console/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain securityFilterChain(HttpSecurity http, LoginAttemptLimiter limiter) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.svg",
                                "/api/gallery", "/api/gallery/**",
                                "/api/categories", "/api/categories/**",
                                "/api/articles", "/api/articles/**",
                                "/api/settings",
                                "/articles", "/articles/**",
                                "/contact", "/admin",
                                "/uploads/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                        .cacheControl(HeadersConfigurer.CacheControlConfig::disable)
                        .addHeaderWriter(new CacheHeaderWriter()))
                .httpBasic(Customizer.withDefaults())
                // Ahead of the Basic filter so a locked key is refused without
                // paying for a bcrypt comparison.
                .addFilterBefore(new AdminLoginThrottleFilter(limiter), BasicAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService(AdminUserRepository users) {
        return username -> users.findByUsername(username)
                .map(user -> User.withUsername(user.getUsername())
                        .password(user.getPasswordHash())
                        .roles("ADMIN")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(username));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
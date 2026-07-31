package web.tosunsaeng.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import web.tosunsaeng.global.config.security.InternalApiKeyAuthenticationFilter;
import web.tosunsaeng.global.config.security.InternalApiKeyVerifier;
import web.tosunsaeng.global.config.security.InternalApiProperties;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InternalApiProperties.class)
public class InternalApiSecurityConfig {

    @Bean
    public InternalApiKeyVerifier internalApiKeyVerifier(
            InternalApiProperties properties) {
        return new InternalApiKeyVerifier(properties);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain internalApiSecurityFilterChain(
            HttpSecurity http,
            InternalApiKeyVerifier verifier,
            ObjectMapper objectMapper) throws Exception {
        InternalApiKeyAuthenticationFilter filter =
                new InternalApiKeyAuthenticationFilter(verifier, objectMapper);

        http
                .securityMatcher("/internal/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().hasAuthority(
                                InternalApiKeyAuthenticationFilter.INTERNAL_AUTHORITY))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                InternalApiKeyAuthenticationFilter.writeFailure(
                                        response,
                                        objectMapper,
                                        ErrorStatus._INTERNAL_API_UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) ->
                                InternalApiKeyAuthenticationFilter.writeFailure(
                                        response,
                                        objectMapper,
                                        ErrorStatus._FORBIDDEN)))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

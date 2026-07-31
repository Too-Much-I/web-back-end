package web.tosunsaeng.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import web.tosunsaeng.global.config.security.JwtAuthenticationFilter;
import web.tosunsaeng.global.config.security.JwtTokenProvider;

@Configuration
public class SecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http, JwtTokenProvider jwtTokenProvider) throws Exception {
        http
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration corsConfiguration = new CorsConfiguration();

                    // 🌟 로컬 테스트 주소와 운영 서버의 정식 도메인 주소(www 포함)를 모두 안전하게 허용합니다.
                    corsConfiguration.setAllowedOrigins(List.of(
                            "http://localhost:5173",
                            "http://localhost:3000",
                            "https://to-teacher.com",
                            "https://www.to-teacher.com"
                    ));

                    corsConfiguration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
                    corsConfiguration.setAllowedHeaders(List.of("*"));
                    corsConfiguration.setAllowCredentials(true);
                    corsConfiguration.setExposedHeaders(List.of("Authorization"));
                    return corsConfiguration;
                }))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 로컬 및 PoC 자유 테스트를 위해 전체 허용 설정 유지
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll()
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}

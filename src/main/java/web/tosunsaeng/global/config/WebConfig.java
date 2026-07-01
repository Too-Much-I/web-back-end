package web.tosunsaeng.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 1. "/api/"로 시작하는 모든 요청
                .allowedOrigins("http://localhost:5173")   // 2. (중요) 모든 출처(도메인)의 요청을 허용
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS") // 3. 허용할 HTTP 메서드
                .allowedHeaders("*")   // 4. 허용할 HTTP 헤더
                .allowCredentials(true) // 5. (쿠키 등 자격증명은 일단 비허용)
                .maxAge(3600);         // 6. (Preflight 요청 캐시 시간)
    }
}
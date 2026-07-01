package web.tosunsaeng.global.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secretKeyString;

    private SecretKey secretKey;

    // 1시간 (밀리초)
    private final long tokenValidTime = 60 * 60 * 1000L;

    @PostConstruct
    protected void init() {
        // 평문 secret 키를 바이트 배열로 변환하여 HmacSha 키 생성
        this.secretKey = Keys.hmacShaKeyFor(secretKeyString.getBytes(StandardCharsets.UTF_8));
    }

    // JWT 토큰 생성 (테스트용)
    public String createToken(String userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .claim("role", "ROLE_USER")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + tokenValidTime))
                .signWith(secretKey)
                .compact();
    }

    // JWT 토큰에서 인증 정보 조회
    public Authentication getAuthentication(String token) {
        Claims claims = getClaims(token);
        // 권한 정보 가져오기 (기본값 부여)
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(claims.get("role", String.class));
        User principal = new User(claims.getSubject(), "", Collections.singletonList(authority));
        return new UsernamePasswordAuthenticationToken(principal, token, Collections.singletonList(authority));
    }

    // 토큰 유효성 및 만료일자 확인
    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.info("잘못된 JWT 서명입니다.");
        } catch (ExpiredJwtException e) {
            log.info("만료된 JWT 토큰입니다.");
        } catch (UnsupportedJwtException e) {
            log.info("지원되지 않는 JWT 토큰입니다.");
        } catch (IllegalArgumentException e) {
            log.info("JWT 토큰이 잘못되었습니다.");
        }
        return false;
    }

    // 토큰에서 클레임(Payload) 추출
    private Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
package web.tosunsaeng.global.config.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import web.tosunsaeng.global.common.response.BaseResponse;
import web.tosunsaeng.global.error.code.status.ErrorStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

public final class InternalApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Internal-Api-Key";
    public static final String INTERNAL_PRINCIPAL = "internal-api";
    public static final String INTERNAL_AUTHORITY = "ROLE_INTERNAL";

    private static final AntPathRequestMatcher INTERNAL_REQUEST =
            new AntPathRequestMatcher("/internal/**");

    private final InternalApiKeyVerifier verifier;
    private final ObjectMapper objectMapper;

    public InternalApiKeyAuthenticationFilter(
            InternalApiKeyVerifier verifier,
            ObjectMapper objectMapper) {
        this.verifier = verifier;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !INTERNAL_REQUEST.matches(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        if (!verifier.isEnabled()) {
            writeFailure(response, objectMapper, ErrorStatus._NOT_FOUND);
            return;
        }

        List<String> headerValues = Collections.list(request.getHeaders(HEADER_NAME));
        String candidate = headerValues.size() == 1 ? headerValues.getFirst() : null;
        if (!verifier.matches(candidate) || headerValues.size() != 1) {
            writeFailure(response, objectMapper, ErrorStatus._INTERNAL_API_UNAUTHORIZED);
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        INTERNAL_PRINCIPAL,
                        null,
                        List.of(new SimpleGrantedAuthority(INTERNAL_AUTHORITY)));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    public static void writeFailure(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            ErrorStatus status) throws IOException {
        response.setStatus(status.getHttpStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        objectMapper.writeValue(
                response.getOutputStream(),
                BaseResponse.onFailure(status, null));
    }
}

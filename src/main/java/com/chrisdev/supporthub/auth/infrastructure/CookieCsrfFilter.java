package com.chrisdev.supporthub.auth.infrastructure;

import com.chrisdev.supporthub.common.exception.BusinessException;
import com.chrisdev.supporthub.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * Doble envío de cookie (double-submit). Se escribe a mano porque el CSRF de Spring Security
 * se desactiva solo ante cualquier token bearer, y eso incluye el que leemos de la cookie.
 */
public class CookieCsrfFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-XSRF-TOKEN";
    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final AuthCookies cookies;
    private final HandlerExceptionResolver resolver;
    private final SecureRandom random = new SecureRandom();

    public CookieCsrfFilter(AuthCookies cookies, HandlerExceptionResolver resolver) {
        this.cookies = cookies;
        this.resolver = resolver;
    }

    private static boolean matches(String header, String cookie) {
        return header != null && !header.isBlank()
                && MessageDigest.isEqual(header.getBytes(StandardCharsets.UTF_8),
                cookie.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String cookieToken = AuthCookies.read(request, AuthCookies.XSRF).orElse(null);
        if (cookieToken == null) {
            cookieToken = newToken();
            response.addHeader(HttpHeaders.SET_COOKIE, cookies.xsrf(cookieToken).toString());
        }

        if (requiresCheck(request) && !matches(request.getHeader(HEADER), cookieToken)) {
            resolver.resolveException(request, response, null,
                    new BusinessException(ErrorCode.CSRF_TOKEN_INVALID, "Missing or invalid CSRF token."));
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean requiresCheck(HttpServletRequest request) {
        return !SAFE_METHODS.contains(request.getMethod())
                && !LOGIN_PATH.equals(request.getRequestURI())
                && request.getHeader(HttpHeaders.AUTHORIZATION) == null
                && (AuthCookies.read(request, AuthCookies.ACCESS).isPresent()
                || AuthCookies.read(request, AuthCookies.REFRESH).isPresent());
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

}

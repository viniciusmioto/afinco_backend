package com.afinco.backend.api.auth;

import com.afinco.backend.api.auth.dto.CsrfTokenResponse;
import com.afinco.backend.api.auth.dto.LoginRequest;
import com.afinco.backend.api.auth.dto.SessionResponse;
import com.afinco.backend.api.auth.dto.UserResponse;
import com.afinco.backend.domain.AppUser;
import com.afinco.backend.exception.AuthenticationFailedException;
import com.afinco.backend.exception.LoginRateLimitException;
import com.afinco.backend.security.AfincoUserPrincipal;
import com.afinco.backend.security.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final LoginAttemptService loginAttemptService;

    public AuthenticationController(
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            LoginAttemptService loginAttemptService) {
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.loginAttemptService = loginAttemptService;
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfTokenResponse> csrf(CsrfToken csrfToken) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CsrfTokenResponse(csrfToken.getToken()));
    }

    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        String clientAddress = request.getRemoteAddr();
        Duration retryAfter = loginAttemptService.retryAfter(clientAddress);
        if (!retryAfter.isZero()) {
            throw new LoginRateLimitException(retryAfter);
        }

        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            AppUser.normalizeEmail(loginRequest.email()),
                            loginRequest.password()));
        } catch (AuthenticationException | IllegalArgumentException exception) {
            Duration failedRetryAfter = loginAttemptService.recordFailure(clientAddress);
            if (!failedRetryAfter.isZero()) {
                throw new LoginRateLimitException(failedRetryAfter);
            }
            throw new AuthenticationFailedException();
        }

        loginAttemptService.recordSuccess(clientAddress);
        request.getSession(true);
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(toResponse((AfincoUserPrincipal) authentication.getPrincipal()));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> currentUser(Authentication authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(toResponse((AfincoUserPrincipal) authentication.getPrincipal()));
    }

    /**
     * Public counterpart of {@code /me}: lets the UI check for a live session without triggering a 401,
     * for example after a backend restart discarded the in-memory session behind a stale cookie.
     */
    @GetMapping("/session")
    public ResponseEntity<SessionResponse> session(Authentication authentication) {
        SessionResponse session = authentication != null
                && authentication.getPrincipal() instanceof AfincoUserPrincipal principal
                ? new SessionResponse(true, toResponse(principal))
                : SessionResponse.anonymous();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(session);
    }

    private UserResponse toResponse(AfincoUserPrincipal principal) {
        return new UserResponse(principal.id(), principal.email());
    }
}

package com.repomind.auth;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public CurrentUserResponse currentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof OAuth2User user)) {
            return new CurrentUserResponse(false, null);
        }

        Map<String, Object> attributes = user.getAttributes();
        String login = value(attributes.get("login"));
        String name = value(attributes.get("name"));
        return new CurrentUserResponse(true, new UserSummary(
                value(attributes.get("id")),
                login,
                name == null || name.isBlank() ? login : name,
                value(attributes.get("avatar_url"))));
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse csrfToken(CsrfToken csrfToken) {
        return new CsrfTokenResponse(csrfToken.getHeaderName(), csrfToken.getToken());
    }

    private String value(Object value) {
        return value == null ? null : value.toString();
    }

    public record CurrentUserResponse(boolean authenticated, UserSummary user) {
    }

    public record UserSummary(String id, String githubLogin, String displayName, String avatarUrl) {
    }

    public record CsrfTokenResponse(String headerName, String token) {
    }
}

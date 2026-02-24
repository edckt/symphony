package com.dbs.symphony.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public final class CurrentPrincipal {
    private CurrentPrincipal() {}

    private static Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    public static String principal() {
        Authentication auth = authentication();
        if (auth == null) return "anonymous";
        Object p = auth.getPrincipal();
        if (p instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return auth.getName();
    }

    public static String claim(String name) {
        Authentication auth = authentication();
        if (auth == null) return null;
        Object p = auth.getPrincipal();
        if (p instanceof Jwt jwt) {
            return jwt.getClaimAsString(name);
        }
        return null;
    }

    /**
     * Resolves the bank user ID used for GCP label lookups.
     * If {@code claim} is non-blank and present in the JWT, returns its value;
     * otherwise falls back to the JWT subject.
     */
    public static String bankUserId(String claim) {
        if (claim != null && !claim.isBlank()) {
            String value = claim(claim);
            if (value != null) return value;
        }
        return principal();
    }

    public static List<String> roles() {
        Authentication auth = authentication();
        if (auth == null) return List.of();
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("SCOPE_") ? a.substring("SCOPE_".length()) : a)
                .map(a -> a.startsWith("ROLE_") ? a.substring("ROLE_".length()) : a)
                .distinct()
                .toList();
    }
}

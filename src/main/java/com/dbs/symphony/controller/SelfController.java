package com.dbs.symphony.controller;

import com.dbs.symphony.dto.SelfResponse;
import com.dbs.symphony.security.CurrentPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SelfController {

    @GetMapping("/v1/self")
    public SelfResponse getSelf() {
        var roles = CurrentPrincipal.roles();
        return new SelfResponse(
            CurrentPrincipal.principal(),
            firstNonBlank(CurrentPrincipal.claim("name"), CurrentPrincipal.claim("preferred_username")),
            firstNonBlank(CurrentPrincipal.claim("email"), CurrentPrincipal.claim("upn")),
            roles.isEmpty() ? java.util.List.of("USER") : roles
        );
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        if (second != null && !second.isBlank()) return second;
        return null;
    }
}

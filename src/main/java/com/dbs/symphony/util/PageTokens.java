package com.dbs.symphony.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Encodes/decodes opaque page tokens for cursor-based pagination.
 * The token is a Base64URL-encoded integer offset into the full result list.
 */
public final class PageTokens {

    private PageTokens() {}

    public static String encode(int offset) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(String.valueOf(offset).getBytes(StandardCharsets.UTF_8));
    }

    public static int decode(String token) {
        if (token == null || token.isBlank()) return 0;
        try {
            return Integer.parseInt(new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return 0;
        }
    }
}

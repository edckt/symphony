package com.dbs.symphony.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Converts between the public operation ID (Base64URL-encoded) and the internal GCP LRO name.
 */
public final class OperationIdCodec {

    private OperationIdCodec() {}

    /** Encodes a GCP LRO resource name into a URL-safe, opaque operation ID. */
    public static String encode(String lroName) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(lroName.getBytes(StandardCharsets.UTF_8));
    }

    /** Decodes an operation ID back to the GCP LRO resource name. */
    public static String decode(String operationId) {
        return new String(Base64.getUrlDecoder().decode(operationId), StandardCharsets.UTF_8);
    }
}

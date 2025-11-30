package com.smartletter.security;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Immutable representation of the API key metadata derived by the authentication filter.
 */
@Getter
@Builder
@ToString
public class ApiKeyMetadata {

    private final String keyIdHash;
    private final String ownerSlug;
    private final String environment;
    private final String rateLimitBucket;

    public static ApiKeyMetadata systemFallback() {
        return ApiKeyMetadata.builder()
                .keyIdHash("unknown")
                .ownerSlug("system")
                .environment("unknown")
                .rateLimitBucket("default")
                .build();
    }
}

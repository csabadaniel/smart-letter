package com.smartletter.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiKeyMetadataResolverTest {

    private final ApiKeyMetadataResolver resolver = new ApiKeyMetadataResolver();

    @Test
    void resolveReturnsFallbackWhenAttributeMissing() {
        HttpServletRequest request = new MockHttpServletRequest();

        ApiKeyMetadata metadata = resolver.resolve(request);

        assertThat(metadata)
                .usingRecursiveComparison()
                .isEqualTo(ApiKeyMetadata.systemFallback());
    }

    @Test
    void resolveReturnsStoredMetadata() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ApiKeyMetadata expected = ApiKeyMetadata.builder()
                .keyIdHash("abc123")
                .ownerSlug("ops")
                .environment("test")
                .rateLimitBucket("default")
                .build();

        resolver.store(request, expected);

        assertThat(resolver.resolve(request)).isEqualTo(expected);
    }

    @Test
    void storeNullPersistsFallback() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        resolver.store(request, null);

        assertThat(resolver.resolve(request))
                .usingRecursiveComparison()
                .isEqualTo(ApiKeyMetadata.systemFallback());
    }
}

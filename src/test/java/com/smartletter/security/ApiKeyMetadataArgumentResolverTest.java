package com.smartletter.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class ApiKeyMetadataArgumentResolverTest {

    private ApiKeyMetadataArgumentResolver argumentResolver;

    @BeforeEach
    void setUp() {
        argumentResolver = new ApiKeyMetadataArgumentResolver(new ApiKeyMetadataResolver());
    }

    @Test
    void supportsApiKeyMetadataParametersOnly() throws NoSuchMethodException {
        Method method = SampleController.class.getDeclaredMethod("handle", ApiKeyMetadata.class);
        MethodParameter metadataParameter = MethodParameter.forParameter(method.getParameters()[0]);
        Method otherMethod = SampleController.class.getDeclaredMethod("handleWithoutMetadata", String.class);
        MethodParameter otherParameter = MethodParameter.forParameter(otherMethod.getParameters()[0]);

        assertThat(argumentResolver.supportsParameter(metadataParameter)).isTrue();
        assertThat(argumentResolver.supportsParameter(otherParameter)).isFalse();
    }

    @Test
    void resolveReturnsMetadataFromRequestAttribute() throws Exception {
        Method method = SampleController.class.getDeclaredMethod("handle", ApiKeyMetadata.class);
        MethodParameter parameter = MethodParameter.forParameter(method.getParameters()[0]);
        MockHttpServletRequest request = new MockHttpServletRequest();
        ApiKeyMetadata metadata = ApiKeyMetadata.builder()
                .keyIdHash("hash")
                .ownerSlug("ops")
                .environment("test")
                .rateLimitBucket("burst-10")
                .build();
        request.setAttribute(ApiKeyMetadataResolver.REQUEST_ATTRIBUTE, metadata);
        ServletWebRequest webRequest = new ServletWebRequest(request);

        Object resolved = argumentResolver.resolveArgument(parameter, null, webRequest, null);

        assertThat(resolved).isEqualTo(metadata);
    }

    @Test
    void resolveReturnsFallbackWhenRequestMissing() throws Exception {
        Method method = SampleController.class.getDeclaredMethod("handle", ApiKeyMetadata.class);
        MethodParameter parameter = MethodParameter.forParameter(method.getParameters()[0]);
        ServletWebRequest webRequest = new ServletWebRequest(new MockHttpServletRequest()) {
            @Override
            public <T> T getNativeRequest(Class<T> requiredType) {
                return null;
            }
        };

        Object resolved = argumentResolver.resolveArgument(parameter, null, webRequest, null);

        assertThat(resolved).usingRecursiveComparison().isEqualTo(ApiKeyMetadata.systemFallback());
    }

    static class SampleController {
        void handle(ApiKeyMetadata metadata) {
        }

        void handleWithoutMetadata(String value) {
        }
    }
}

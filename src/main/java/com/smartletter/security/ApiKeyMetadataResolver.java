package com.smartletter.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * Centralizes storage and retrieval of {@link ApiKeyMetadata} from the HTTP request lifecycle.
 */
@Component
public class ApiKeyMetadataResolver {

    public static final String REQUEST_ATTRIBUTE = ApiKeyMetadataResolver.class.getName() + ".API_KEY_METADATA";

    public ApiKeyMetadata resolve(HttpServletRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Object attribute = request.getAttribute(REQUEST_ATTRIBUTE);
        if (attribute instanceof ApiKeyMetadata metadata) {
            return metadata;
        }
        return ApiKeyMetadata.systemFallback();
    }

    public void store(HttpServletRequest request, ApiKeyMetadata metadata) {
        Objects.requireNonNull(request, "request must not be null");
        request.setAttribute(REQUEST_ATTRIBUTE, metadata != null ? metadata : ApiKeyMetadata.systemFallback());
    }
}

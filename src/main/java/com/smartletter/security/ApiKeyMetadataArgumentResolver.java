package com.smartletter.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * HandlerMethodArgumentResolver that injects {@link ApiKeyMetadata} into controller methods.
 */
@Component
public class ApiKeyMetadataArgumentResolver implements HandlerMethodArgumentResolver {

    private final ApiKeyMetadataResolver resolver;

    public ApiKeyMetadataArgumentResolver(ApiKeyMetadataResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public boolean supportsParameter(@NonNull MethodParameter parameter) {
        return ApiKeyMetadata.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(@Nullable MethodParameter parameter, @Nullable ModelAndViewContainer mavContainer,
            @NonNull NativeWebRequest webRequest, @Nullable WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            return ApiKeyMetadata.systemFallback();
        }
        return resolver.resolve(request);
    }
}

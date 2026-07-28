package com.posdesktop.pos.auth.web;

import com.posdesktop.pos.auth.exception.UnauthorizedException;
import com.posdesktop.pos.auth.service.AuthService;
import com.posdesktop.pos.auth.service.AuthSessionData;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        if (isPublic(handlerMethod)) {
            return true;
        }

        String token = request.getHeader(AuthService.AUTH_HEADER);
        if (token == null || token.isBlank()) {
            throw new UnauthorizedException("Debes iniciar sesion para acceder a este recurso.");
        }

        AuthSessionData session = authService.validateSession(token);
        request.setAttribute(AuthService.REQUEST_CONTEXT, session);

        RequiresPermissions required = resolvePermissions(handlerMethod);
        if (required != null) {
            authService.ensurePermissions(session, required.value());
        }
        return true;
    }

    private boolean isPublic(HandlerMethod handlerMethod) {
        return handlerMethod.getMethodAnnotation(PublicEndpoint.class) != null
                || handlerMethod.getBeanType().getAnnotation(PublicEndpoint.class) != null;
    }

    private RequiresPermissions resolvePermissions(HandlerMethod handlerMethod) {
        RequiresPermissions methodAnnotation = handlerMethod.getMethodAnnotation(RequiresPermissions.class);
        if (methodAnnotation != null) {
            return methodAnnotation;
        }
        return handlerMethod.getBeanType().getAnnotation(RequiresPermissions.class);
    }
}

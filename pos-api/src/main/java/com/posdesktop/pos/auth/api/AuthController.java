package com.posdesktop.pos.auth.api;

import com.posdesktop.pos.auth.api.dto.AuthSessionResponse;
import com.posdesktop.pos.auth.api.dto.LoginRequest;
import com.posdesktop.pos.auth.service.AuthMapper;
import com.posdesktop.pos.auth.service.AuthService;
import com.posdesktop.pos.auth.service.AuthSessionData;
import com.posdesktop.pos.auth.web.PublicEndpoint;
import com.posdesktop.pos.shared.api.ApiPaths;
import com.posdesktop.pos.shared.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.AUTH)
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PublicEndpoint
    @PostMapping("/login")
    public ApiResponse<AuthSessionResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        return ApiResponse.success(
                "Inicio de sesion correcto.",
                httpRequest.getRequestURI(),
                AuthMapper.toResponse(authService.login(request))
        );
    }

    @GetMapping("/me")
    public ApiResponse<AuthSessionResponse> currentSession(
            @RequestHeader(AuthService.AUTH_HEADER) String token,
            HttpServletRequest httpRequest
    ) {
        AuthSessionData session = authService.validateSession(token);
        return ApiResponse.success(
                "Sesion consultada correctamente.",
                httpRequest.getRequestURI(),
                AuthMapper.toResponse(session)
        );
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(value = AuthService.AUTH_HEADER, required = false) String token,
            HttpServletRequest httpRequest
    ) {
        authService.logout(token);
        return ApiResponse.success(
                "Sesion cerrada correctamente.",
                httpRequest.getRequestURI(),
                null
        );
    }
}

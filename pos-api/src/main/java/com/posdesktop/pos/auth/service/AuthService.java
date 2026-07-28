package com.posdesktop.pos.auth.service;

import com.posdesktop.pos.auth.api.dto.LoginRequest;
import com.posdesktop.pos.auth.config.AuthProperties;
import com.posdesktop.pos.auth.exception.ForbiddenException;
import com.posdesktop.pos.auth.exception.UnauthorizedException;
import com.posdesktop.pos.modelo.relacional.PermisoSistema;
import com.posdesktop.pos.modelo.relacional.RolSistema;
import com.posdesktop.pos.modelo.relacional.SesionUsuario;
import com.posdesktop.pos.modelo.relacional.UsuarioSistema;
import com.posdesktop.pos.repositorio.relacional.SesionUsuarioRepositorio;
import com.posdesktop.pos.repositorio.relacional.UsuarioSistemaRepositorio;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    public static final String AUTH_HEADER = "X-Pos-Auth";
    public static final String REQUEST_CONTEXT = "pos.auth.context";
    private final UsuarioSistemaRepositorio usuarioSistemaRepositorio;
    private final SesionUsuarioRepositorio sesionUsuarioRepositorio;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UsuarioSistemaRepositorio usuarioSistemaRepositorio,
            SesionUsuarioRepositorio sesionUsuarioRepositorio,
            PasswordEncoder passwordEncoder,
            AuthProperties authProperties
    ) {
        this.usuarioSistemaRepositorio = usuarioSistemaRepositorio;
        this.sesionUsuarioRepositorio = sesionUsuarioRepositorio;
        this.passwordEncoder = passwordEncoder;
        this.authProperties = authProperties;
    }

    @Transactional
    public AuthSessionData login(LoginRequest request) {
        UsuarioSistema usuario = usuarioSistemaRepositorio.findByUsernameIgnoreCase(normalizeUsername(request.username()))
                .orElseThrow(() -> new UnauthorizedException("Usuario o clave invalida."));
        if (!usuario.isActivo()) {
            throw new ForbiddenException("El usuario enviado se encuentra inactivo.");
        }
        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            throw new UnauthorizedException("Usuario o clave invalida.");
        }

        LocalDateTime now = LocalDateTime.now();
        SesionUsuario sesion = new SesionUsuario();
        sesion.setUsuario(usuario);
        sesion.setToken(generateToken());
        sesion.setUltimoAcceso(now);
        sesion.setExpiraEn(now.plusHours(Math.max(1, authProperties.sessionHours())));
        sesion.setActiva(true);
        sesionUsuarioRepositorio.save(sesion);

        usuario.setUltimoIngreso(now);
        usuarioSistemaRepositorio.save(usuario);
        return mapSession(sesion);
    }

    @Transactional
    public AuthSessionData validateSession(String token) {
        String normalizedToken = cleanToken(token);
        if (normalizedToken == null) {
            throw new UnauthorizedException("Debes iniciar sesion para acceder a este recurso.");
        }
        SesionUsuario sesion = sesionUsuarioRepositorio.findByTokenAndActivaTrue(normalizedToken)
                .orElseThrow(() -> new UnauthorizedException("La sesion no es valida o ya expiro."));
        if (!sesion.isActiva() || sesion.getExpiraEn().isBefore(LocalDateTime.now())) {
            sesion.setActiva(false);
            sesionUsuarioRepositorio.save(sesion);
            throw new UnauthorizedException("La sesion ya expiro. Inicia sesion de nuevo.");
        }
        if (sesion.getUsuario() == null || !sesion.getUsuario().isActivo()) {
            throw new ForbiddenException("El usuario asociado a la sesion no esta habilitado.");
        }

        sesion.setUltimoAcceso(LocalDateTime.now());
        sesionUsuarioRepositorio.save(sesion);
        return mapSession(sesion);
    }

    @Transactional
    public void logout(String token) {
        String normalized = cleanToken(token);
        if (normalized == null) {
            return;
        }
        sesionUsuarioRepositorio.findByTokenAndActivaTrue(normalized).ifPresent(sesion -> {
            sesion.setActiva(false);
            sesionUsuarioRepositorio.save(sesion);
        });
    }

    public void ensurePermissions(AuthSessionData session, String[] requiredPermissions) {
        if (requiredPermissions == null || requiredPermissions.length == 0) {
            return;
        }
        Set<String> granted = session.permisos();
        List<String> missing = java.util.Arrays.stream(requiredPermissions)
                .filter(permission -> !granted.contains(permission))
                .toList();
        if (!missing.isEmpty()) {
            throw new ForbiddenException("No tienes permiso para ejecutar esta accion: " + String.join(", ", missing) + ".");
        }
    }

    private AuthSessionData mapSession(SesionUsuario sesion) {
        UsuarioSistema usuario = sesion.getUsuario();
        Set<String> roles = usuario.getRoles().stream()
                .filter(RolSistema::isActivo)
                .map(RolSistema::getCodigo)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> permisos = usuario.getRoles().stream()
                .filter(RolSistema::isActivo)
                .flatMap(rol -> rol.getPermisos().stream())
                .filter(PermisoSistema::isActivo)
                .map(PermisoSistema::getCodigo)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return new AuthSessionData(
                sesion.getToken(),
                sesion.getExpiraEn(),
                usuario.getId().toString(),
                usuario.getUsername(),
                usuario.getNombreCompleto(),
                roles,
                permisos
        );
    }

    private String generateToken() {
        byte[] bytes = new byte[36];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeUsername(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase();
    }

    private String cleanToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return token.trim();
    }
}

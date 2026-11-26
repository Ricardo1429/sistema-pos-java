package com.restobar.pos.config;

import com.restobar.pos.model.Usuario;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Expone en todos los modelos los atributos usados por el layout:
 * {@code usuarioActual} (entidad Usuario) y {@code rolActual} (nombre del rol).
 */
@ControllerAdvice
public class GlobalModelAttributes {

    private final UsuarioActualResolver usuarioActualResolver;

    public GlobalModelAttributes(UsuarioActualResolver usuarioActualResolver) {
        this.usuarioActualResolver = usuarioActualResolver;
    }

    @ModelAttribute
    public void atributosGlobales(Authentication authentication, Model model) {
        Usuario usuario = usuarioActualResolver.resolver(authentication);
        model.addAttribute("usuarioActual", usuario);
        model.addAttribute("rolActual",
                usuario != null && usuario.getRol() != null ? usuario.getRol().name() : null);
    }
}

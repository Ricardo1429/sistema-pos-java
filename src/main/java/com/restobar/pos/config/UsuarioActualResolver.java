package com.restobar.pos.config;

import com.restobar.pos.model.Usuario;
import com.restobar.pos.repository.UsuarioRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Resuelve la entidad {@link Usuario} que corresponde a la identidad autenticada.
 *
 * <p>Existe para evitar que la identidad llegue a los controladores como parámetro
 * {@code @ModelAttribute}: Spring MVC enlazaría los parámetros de la request (incluidos
 * los path variables) sobre la entidad JPA y mutaría su identificador, provocando
 * "Identifier of an instance of 'Usuario' was altered" al hacer flush.</p>
 */
@Component
public class UsuarioActualResolver {

    private final UsuarioRepository usuarioRepository;

    public UsuarioActualResolver(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * @return el usuario autenticado o {@code null} si no hay identidad válida.
     */
    public Usuario resolver(Authentication authentication) {
        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return null;
        }
        return usuarioRepository.findByUsername(authentication.getName()).orElse(null);
    }
}

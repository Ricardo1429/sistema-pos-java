package com.restobar.pos.controller;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Muestra el formulario de inicio de sesión. El POST lo procesa Spring Security
 * ({@code loginProcessingUrl("/login")} en {@code SecurityConfig}).
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(Authentication authentication) {
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/";
        }
        return "login";
    }

    @GetMapping("/error/acceso")
    public String accesoDenegado() {
        return "error/acceso";
    }
}

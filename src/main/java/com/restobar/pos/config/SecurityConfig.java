package com.restobar.pos.config;

import com.restobar.pos.repository.UsuarioRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(UsuarioRepository usuarioRepository) {
        return username -> usuarioRepository.findByUsername(username)
                .map(usuario -> User.withUsername(usuario.getUsername())
                        .password(usuario.getPassword())
                        .authorities("ROLE_" + usuario.getRol().name())
                        .disabled(!usuario.isActivo())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/error", "/error/**", "/css/**", "/js/**", "/img/**", "/webjars/**")
                        .permitAll()
                        .requestMatchers("/menu/**").hasAnyRole("ADMIN", "SUPERVISOR")
                        .requestMatchers("/cocina/**").hasAnyRole("ADMIN", "COCINERO_BARMAN", "SUPERVISOR")
                        .requestMatchers("/caja/**").hasAnyRole("ADMIN", "CAJERO", "SUPERVISOR")
                        .requestMatchers("/mesas/**", "/ordenes/**")
                        .hasAnyRole("ADMIN", "MESERO", "CAJERO", "SUPERVISOR")
                        .requestMatchers("/h2-console/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .permitAll())
                .exceptionHandling(ex -> ex.accessDeniedHandler((request, response, accessDeniedException) -> response.sendRedirect(request.getContextPath() + "/error/acceso")))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/h2-console/**"))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()));
        return http.build();
    }
}

package co.edu.javeriana.procesosempresariales.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

final class UsuariosSimulados {

    private UsuariosSimulados() {
    }

    static void activos(UserDetailsService userDetailsService) {
        when(userDetailsService.loadUserByUsername(anyString()))
                .thenAnswer(invocacion -> User.withUsername(invocacion.getArgument(0, String.class))
                        .password("")
                        .build());
    }
}

package co.edu.javeriana.procesosempresariales.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;

public class UsuarioActivoFilter extends OncePerRequestFilter {

    private final UserDetailsService userDetailsService;
    private final SecurityContextHolderStrategy contexto = SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextLogoutHandler cierreSesion = new SecurityContextLogoutHandler();

    public UsuarioActivoFilter(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
        this.cierreSesion.setSecurityContextHolderStrategy(contexto);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication autenticacion = contexto.getContext().getAuthentication();
        if (esUsuarioAutenticado(autenticacion) && !sigueHabilitado(autenticacion.getName())) {
            cierreSesion.logout(request, response, autenticacion);
        }
        filterChain.doFilter(request, response);
    }

    private boolean esUsuarioAutenticado(Authentication autenticacion) {
        return autenticacion != null && autenticacion.isAuthenticated()
                && !(autenticacion instanceof AnonymousAuthenticationToken);
    }

    private boolean sigueHabilitado(String username) {
        try {
            UserDetails usuario = userDetailsService.loadUserByUsername(username);
            return usuario != null && usuario.isEnabled();
        } catch (UsernameNotFoundException e) {
            return false;
        }
    }
}

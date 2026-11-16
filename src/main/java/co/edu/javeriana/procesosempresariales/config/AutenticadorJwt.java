package co.edu.javeriana.procesosempresariales.config;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import co.edu.javeriana.procesosempresariales.service.FamiliaRefrescoService;

@Component
public class AutenticadorJwt {

    static final String SESION_NO_VALIDA = "La sesión del token ya no es válida";

    private UserDetailsService userDetailsService;
    private FamiliaRefrescoService familiaRefrescoService;

    @Autowired
    public AutenticadorJwt(UserDetailsService userDetailsService, FamiliaRefrescoService familiaRefrescoService) {
        this.userDetailsService = userDetailsService;
        this.familiaRefrescoService = familiaRefrescoService;
    }

    public AbstractAuthenticationToken autenticar(Jwt jwt) {
        String correo = jwt.getSubject();
        UUID familia = UUID.fromString(jwt.getClaimAsString(JwtConfig.CLAIM_FAMILIA));
        if (!familiaRefrescoService.estaActivaPara(familia, correo)) {
            throw new InvalidBearerTokenException(SESION_NO_VALIDA);
        }
        UserDetails usuario = cargar(correo);
        if (!usuario.isEnabled()) {
            throw new InvalidBearerTokenException(SESION_NO_VALIDA);
        }
        return new JwtAuthenticationToken(jwt, usuario.getAuthorities(), usuario.getUsername());
    }

    private UserDetails cargar(String correo) {
        try {
            return userDetailsService.loadUserByUsername(correo);
        } catch (UsernameNotFoundException e) {
            throw new InvalidBearerTokenException(SESION_NO_VALIDA);
        }
    }
}

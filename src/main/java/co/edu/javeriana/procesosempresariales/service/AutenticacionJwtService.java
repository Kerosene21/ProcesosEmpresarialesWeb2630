package co.edu.javeriana.procesosempresariales.service;

import java.time.Clock;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.config.RefrescoProperties;
import co.edu.javeriana.procesosempresariales.domain.FamiliaRefresco;
import co.edu.javeriana.procesosempresariales.domain.MotivoRevocacion;
import co.edu.javeriana.procesosempresariales.domain.RefreshToken;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.exception.CredencialesInvalidasException;
import co.edu.javeriana.procesosempresariales.exception.SesionExpiradaException;
import co.edu.javeriana.procesosempresariales.repository.FamiliaRefrescoRepository;
import co.edu.javeriana.procesosempresariales.repository.RefreshTokenRepository;

@Service
public class AutenticacionJwtService {

    static final String CREDENCIALES_INVALIDAS = "Correo o contraseña incorrectos";
    static final String SESION_EXPIRADA = "La sesión expiró; inicia sesión de nuevo";

    private DaoAuthenticationProvider proveedorCredenciales;
    private UsuarioService usuarioService;
    private SesionService sesionService;
    private TokenAccesoService tokenAccesoService;
    private CodigoRefresco codigoRefresco;
    private FamiliaRefrescoRepository familiaRefrescoRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private FamiliaRefrescoService familiaRefrescoService;
    private RefrescoProperties refrescoProperties;
    private Clock reloj;

    @Autowired
    public AutenticacionJwtService(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder,
            UsuarioService usuarioService, SesionService sesionService, TokenAccesoService tokenAccesoService,
            CodigoRefresco codigoRefresco, FamiliaRefrescoRepository familiaRefrescoRepository,
            RefreshTokenRepository refreshTokenRepository, FamiliaRefrescoService familiaRefrescoService,
            RefrescoProperties refrescoProperties, Clock reloj) {
        this.proveedorCredenciales = new DaoAuthenticationProvider(userDetailsService);
        this.proveedorCredenciales.setPasswordEncoder(passwordEncoder);
        this.usuarioService = usuarioService;
        this.sesionService = sesionService;
        this.tokenAccesoService = tokenAccesoService;
        this.codigoRefresco = codigoRefresco;
        this.familiaRefrescoRepository = familiaRefrescoRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.familiaRefrescoService = familiaRefrescoService;
        this.refrescoProperties = refrescoProperties;
        this.reloj = reloj;
    }

    @Transactional
    public SesionJwt iniciarSesion(String correo, String password) {
        Authentication autenticado;
        try {
            autenticado = proveedorCredenciales.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(correo, password));
        } catch (AuthenticationException e) {
            throw new CredencialesInvalidasException(CREDENCIALES_INVALIDAS);
        }
        Usuario usuario = usuarioService.usuarioAutenticado(autenticado.getName());
        Instant ahora = reloj.instant();
        FamiliaRefresco familia = familiaRefrescoRepository.save(new FamiliaRefresco(null, usuario, ahora,
                ahora.plus(refrescoProperties.duracionMaxima()), null, null));
        return emitirConRefresco(usuario, familia, ahora);
    }

    @Transactional(noRollbackFor = SesionExpiradaException.class)
    public SesionJwt refrescar(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            throw new SesionExpiradaException(SESION_EXPIRADA);
        }
        RefreshToken presentado = refreshTokenRepository.buscarParaRotar(codigoRefresco.hash(codigo))
                .orElseThrow(() -> new SesionExpiradaException(SESION_EXPIRADA));
        FamiliaRefresco familia = presentado.getFamilia();
        Usuario usuario = familia.getUsuario();
        Instant ahora = reloj.instant();
        if (familia.getRevocadaEn() != null || !familia.getExpiraEn().isAfter(ahora)) {
            throw new SesionExpiradaException(SESION_EXPIRADA);
        }
        if (!usuario.isActivo()) {
            familiaRefrescoService.revocar(familia.getId(), MotivoRevocacion.USUARIO_DESACTIVADO);
            throw new SesionExpiradaException(SESION_EXPIRADA);
        }
        if (presentado.getUsadoEn() != null) {
            if (dentroDeLaGracia(presentado, ahora)) {
                return emitirSinRefresco(usuario, familia, presentado.getUsadoEn());
            }
            familiaRefrescoService.revocar(familia.getId(), MotivoRevocacion.REUTILIZACION_DETECTADA);
            throw new SesionExpiradaException(SESION_EXPIRADA);
        }
        if (!presentado.getExpiraEn().isAfter(ahora)) {
            throw new SesionExpiradaException(SESION_EXPIRADA);
        }
        presentado.setUsadoEn(ahora);
        return emitirConRefresco(usuario, familia, ahora);
    }

    @Transactional
    public void cerrarSesion(String codigo) {
        if (codigo == null || codigo.isBlank()) {
            return;
        }
        refreshTokenRepository.buscarParaRotar(codigoRefresco.hash(codigo))
                .ifPresent(token -> familiaRefrescoService.revocar(token.getFamilia().getId(),
                        MotivoRevocacion.CIERRE_SESION));
    }

    private boolean dentroDeLaGracia(RefreshToken presentado, Instant ahora) {
        Instant usadoEn = presentado.getUsadoEn();
        boolean reciente = !ahora.isAfter(usadoEn.plus(refrescoProperties.graciaRotacion()));
        return reciente && refreshTokenRepository.existsByFamiliaIdAndUsadoEnIsNullAndEmitidoEnGreaterThanEqual(
                presentado.getFamilia().getId(), usadoEn);
    }

    private SesionJwt emitirConRefresco(Usuario usuario, FamiliaRefresco familia, Instant ahora) {
        String codigo = codigoRefresco.generar();
        Instant expiraEn = minimo(ahora.plus(refrescoProperties.duracion()), familia.getExpiraEn());
        refreshTokenRepository.save(new RefreshToken(null, familia, codigoRefresco.hash(codigo), ahora, expiraEn,
                null));
        TokenAccesoEmitido acceso = tokenAccesoService.emitir(usuario.getUsername(), familia.getId());
        return new SesionJwt(acceso, sesionService.consultar(usuario.getUsername()), codigo, expiraEn);
    }

    private SesionJwt emitirSinRefresco(Usuario usuario, FamiliaRefresco familia, Instant rotadoEn) {
        Instant limite = rotadoEn.plus(tokenAccesoService.duracionAcceso());
        TokenAccesoEmitido acceso = tokenAccesoService.emitir(usuario.getUsername(), familia.getId(), limite);
        return new SesionJwt(acceso, sesionService.consultar(usuario.getUsername()), null, null);
    }

    private static Instant minimo(Instant uno, Instant otro) {
        return uno.isBefore(otro) ? uno : otro;
    }
}

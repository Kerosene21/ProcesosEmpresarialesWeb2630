package co.edu.javeriana.procesosempresariales.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.config.JwtProperties;
import co.edu.javeriana.procesosempresariales.domain.MotivoRevocacion;
import co.edu.javeriana.procesosempresariales.repository.FamiliaRefrescoRepository;
import co.edu.javeriana.procesosempresariales.repository.RefreshTokenRepository;

@Service
public class FamiliaRefrescoService {

    public static final long CLAVE_BLOQUEO_PURGA = 0x5045_5245_4652_4553L;

    private FamiliaRefrescoRepository familiaRefrescoRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private JwtProperties jwtProperties;
    private Clock reloj;

    @Autowired
    public FamiliaRefrescoService(FamiliaRefrescoRepository familiaRefrescoRepository,
            RefreshTokenRepository refreshTokenRepository, JwtProperties jwtProperties, Clock reloj) {
        this.familiaRefrescoRepository = familiaRefrescoRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtProperties = jwtProperties;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    public boolean estaRevocada(UUID familiaId) {
        return familiaId == null || !familiaRefrescoRepository.existsByIdAndRevocadaEnIsNull(familiaId);
    }

    @Transactional(readOnly = true)
    public boolean estaActivaPara(UUID familiaId, String username) {
        return familiaId != null && username != null
                && familiaRefrescoRepository.existsByIdAndUsuarioUsernameAndRevocadaEnIsNull(familiaId, username);
    }

    @Transactional
    public boolean revocar(UUID familiaId, MotivoRevocacion motivo) {
        return familiaRefrescoRepository.revocar(familiaId, exigir(motivo), reloj.instant()) > 0;
    }

    @Transactional
    public int revocarDelUsuario(Long usuarioId, MotivoRevocacion motivo) {
        return familiaRefrescoRepository.revocarDelUsuario(usuarioId, exigir(motivo), reloj.instant());
    }

    @Transactional
    public Optional<PurgaRefresco> purgarCaducados() {
        if (!familiaRefrescoRepository.bloquearPurga(CLAVE_BLOQUEO_PURGA)) {
            return Optional.empty();
        }
        Instant ahora = reloj.instant();
        int tokens = refreshTokenRepository.eliminarExpiradosAntesDe(ahora);
        int familias = familiaRefrescoRepository.eliminarExpiradasAntesDe(limiteConservacionFamilias(ahora));
        return Optional.of(new PurgaRefresco(tokens, familias));
    }

    Instant limiteConservacionFamilias(Instant ahora) {
        return ahora.minus(jwtProperties.duracionAcceso()).minus(jwtProperties.toleranciaReloj());
    }

    private MotivoRevocacion exigir(MotivoRevocacion motivo) {
        if (motivo == null) {
            throw new IllegalArgumentException("La revocacion requiere un motivo");
        }
        return motivo;
    }
}

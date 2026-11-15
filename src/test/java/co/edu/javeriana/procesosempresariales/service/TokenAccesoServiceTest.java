package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import co.edu.javeriana.procesosempresariales.config.JwtConfig;
import co.edu.javeriana.procesosempresariales.config.JwtProperties;

class TokenAccesoServiceTest {

    private static final String CORREO = "editor@alpes.com";
    private static final String EMISOR = "procesos-empresariales";
    private static final String AUDIENCIA = "procesos-empresariales-api";
    private static final Instant AHORA = Instant.parse("2026-11-15T10:00:00Z");
    private static final Duration QUINCE_MINUTOS = Duration.ofMinutes(15);
    private static final Duration TOLERANCIA = Duration.ofSeconds(30);

    private final JwtConfig config = new JwtConfig();
    private String secreto;
    private UUID familia;

    @BeforeEach
    void preparar() {
        secreto = secreto(48);
        familia = UUID.randomUUID();
    }

    private static String secreto(int bytes) {
        byte[] clave = new byte[bytes];
        new SecureRandom().nextBytes(clave);
        return Base64.getEncoder().encodeToString(clave);
    }

    private JwtProperties propiedades(String clave, String emisor, String audiencia) {
        return new JwtProperties(clave, emisor, audiencia, QUINCE_MINUTOS, TOLERANCIA);
    }

    private TokenAccesoService servicio(JwtProperties propiedades, Instant instante) {
        Clock reloj = Clock.fixed(instante, ZoneOffset.UTC);
        return new TokenAccesoService(config.jwtEncoder(propiedades), config.jwtDecoder(propiedades, reloj),
                propiedades, reloj);
    }

    private TokenAccesoService servicio(Instant instante) {
        return servicio(propiedades(secreto, EMISOR, AUDIENCIA), instante);
    }

    private String emitir() {
        return servicio(AHORA).emitir(CORREO, familia).valor();
    }

    @Test
    void emiteUnTokenHs256DeQuinceMinutosConLosClaimsEsperados() {
        TokenAccesoEmitido emitido = servicio(AHORA).emitir(CORREO, familia);

        Jwt jwt = servicio(AHORA).decodificar(emitido.valor());

        assertThat(emitido.expiraEn()).isEqualTo(AHORA.plus(QUINCE_MINUTOS));
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256").containsEntry("typ", "JWT");
        assertThat(jwt.getSubject()).isEqualTo(CORREO);
        assertThat(jwt.getClaimAsString("iss")).isEqualTo(EMISOR);
        assertThat(jwt.getAudience()).containsExactly(AUDIENCIA);
        assertThat(jwt.getIssuedAt()).isEqualTo(AHORA);
        assertThat(jwt.getExpiresAt()).isEqualTo(AHORA.plus(QUINCE_MINUTOS));
        assertThat(UUID.fromString(jwt.getId())).isNotNull();
        assertThat(servicio(AHORA).familiaDe(jwt)).isEqualTo(familia);
        assertThat(jwt.getClaims()).doesNotContainKeys("rol", "roles", "empresaId", "scope");
    }

    @Test
    void cadaTokenTieneUnIdentificadorDistinto() {
        Jwt uno = servicio(AHORA).decodificar(emitir());
        Jwt otro = servicio(AHORA).decodificar(emitir());

        assertThat(uno.getId()).isNotEqualTo(otro.getId());
    }

    @Test
    void elTokenSigueSiendoValidoDentroDeLaToleranciaDelReloj() {
        String token = emitir();

        Jwt jwt = servicio(AHORA.plus(QUINCE_MINUTOS).plus(TOLERANCIA)).decodificar(token);

        assertThat(jwt.getSubject()).isEqualTo(CORREO);
    }

    @Test
    void unTokenCaducadoSeRechaza() {
        String token = emitir();

        assertThatThrownBy(() -> servicio(AHORA.plus(QUINCE_MINUTOS).plus(TOLERANCIA).plusSeconds(1))
                .decodificar(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void unTokenFirmadoConOtraClaveSeRechaza() {
        String ajeno = servicio(propiedades(secreto(48), EMISOR, AUDIENCIA), AHORA).emitir(CORREO, familia).valor();

        assertThatThrownBy(() -> servicio(AHORA).decodificar(ajeno)).isInstanceOf(JwtException.class);
    }

    @Test
    void unTokenDeOtroEmisorUOtraAudienciaSeRechaza() {
        String otroEmisor = servicio(propiedades(secreto, "otro-sistema", AUDIENCIA), AHORA)
                .emitir(CORREO, familia).valor();
        String otraAudiencia = servicio(propiedades(secreto, EMISOR, "otra-api"), AHORA)
                .emitir(CORREO, familia).valor();

        assertThatThrownBy(() -> servicio(AHORA).decodificar(otroEmisor)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> servicio(AHORA).decodificar(otraAudiencia)).isInstanceOf(JwtException.class);
    }

    @Test
    void unTokenAlteradoSeRechaza() {
        String[] partes = emitir().split("\\.");
        String cargaAlterada = base64Url("{\"sub\":\"admin@alpes.com\",\"iss\":\"" + EMISOR + "\"}");
        String alterado = partes[0] + "." + cargaAlterada + "." + partes[2];

        assertThatThrownBy(() -> servicio(AHORA).decodificar(alterado)).isInstanceOf(JwtException.class);
    }

    @Test
    void unTokenSinFirmaSeRechaza() {
        String carga = "{\"sub\":\"" + CORREO + "\",\"iss\":\"" + EMISOR + "\",\"aud\":\"" + AUDIENCIA
                + "\",\"jti\":\"" + UUID.randomUUID() + "\",\"sid\":\"" + familia + "\",\"iat\":"
                + AHORA.getEpochSecond() + ",\"exp\":" + AHORA.plus(QUINCE_MINUTOS).getEpochSecond() + "}";
        String sinFirma = base64Url("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "." + base64Url(carga) + ".";

        assertThatThrownBy(() -> servicio(AHORA).decodificar(sinFirma)).isInstanceOf(JwtException.class);
    }

    @Test
    void unTokenConOtroAlgoritmoHmacSeRechaza() {
        String claveLarga = secreto(64);
        JwtProperties propiedades = propiedades(claveLarga, EMISOR, AUDIENCIA);
        JwtEncoder hs512 = NimbusJwtEncoder.withSecretKey(propiedades.claveFirma()).algorithm(MacAlgorithm.HS512)
                .build();
        String token = hs512.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS512).type("JWT").build(),
                claims().build())).getTokenValue();

        assertThatThrownBy(() -> servicio(propiedades, AHORA).decodificar(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void unTokenSinFamiliaOConFamiliaNoValidaSeRechaza() {
        JwtProperties propiedades = propiedades(secreto, EMISOR, AUDIENCIA);
        JwtEncoder codificador = config.jwtEncoder(propiedades);
        String sinFamilia = codificar(codificador, claims().claims(c -> c.remove(JwtConfig.CLAIM_FAMILIA)).build());
        String familiaNoValida = codificar(codificador, claims().claim(JwtConfig.CLAIM_FAMILIA, "no-es-uuid").build());
        String familiaNumerica = codificar(codificador, claims().claim(JwtConfig.CLAIM_FAMILIA, 42).build());

        assertThatThrownBy(() -> servicio(AHORA).decodificar(sinFamilia)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> servicio(AHORA).decodificar(familiaNoValida)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> servicio(AHORA).decodificar(familiaNumerica)).isInstanceOf(JwtException.class);
    }

    @Test
    void unTokenSinExpiracionOSinSujetoSeRechaza() {
        JwtEncoder codificador = config.jwtEncoder(propiedades(secreto, EMISOR, AUDIENCIA));
        String sinExpiracion = codificar(codificador, claims().claims(c -> c.remove("exp")).build());
        String sinSujeto = codificar(codificador, claims().claims(c -> c.remove("sub")).build());

        assertThatThrownBy(() -> servicio(AHORA).decodificar(sinExpiracion)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> servicio(AHORA).decodificar(sinSujeto)).isInstanceOf(JwtException.class);
    }

    @Test
    void unLimiteDeExpiracionAcortaElTokenSinPoderAlargarlo() {
        TokenAccesoService servicio = servicio(AHORA);

        TokenAccesoEmitido acotado = servicio.emitir(CORREO, familia, AHORA.plus(Duration.ofMinutes(5)));
        TokenAccesoEmitido sinEfecto = servicio.emitir(CORREO, familia, AHORA.plus(Duration.ofHours(2)));

        assertThat(acotado.expiraEn()).isEqualTo(AHORA.plus(Duration.ofMinutes(5)));
        assertThat(acotado.duracionEnSegundos()).isEqualTo(300);
        assertThat(servicio.decodificar(acotado.valor()).getExpiresAt()).isEqualTo(AHORA.plus(Duration.ofMinutes(5)));
        assertThat(sinEfecto.expiraEn()).isEqualTo(AHORA.plus(QUINCE_MINUTOS));
        assertThat(sinEfecto.duracionEnSegundos()).isEqualTo(QUINCE_MINUTOS.toSeconds());
    }

    @Test
    void unLimiteYaVencidoNoPermiteEmitir() {
        TokenAccesoService servicio = servicio(AHORA);

        assertThatThrownBy(() -> servicio.emitir(CORREO, familia, AHORA.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emitirExigeCorreoYFamilia() {
        TokenAccesoService servicio = servicio(AHORA);

        assertThatThrownBy(() -> servicio.emitir(" ", familia)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> servicio.emitir(CORREO, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void laRepresentacionEnTextoNoExponeElToken() {
        TokenAccesoEmitido emitido = servicio(AHORA).emitir(CORREO, familia);

        assertThat(emitido.toString()).doesNotContain(emitido.valor());
    }

    private JwtClaimsSet.Builder claims() {
        return JwtClaimsSet.builder()
                .issuer(EMISOR)
                .audience(List.of(AUDIENCIA))
                .subject(CORREO)
                .id(UUID.randomUUID().toString())
                .issuedAt(AHORA)
                .expiresAt(AHORA.plus(QUINCE_MINUTOS))
                .claim(JwtConfig.CLAIM_FAMILIA, familia.toString());
    }

    private String codificar(JwtEncoder codificador, JwtClaimsSet claims) {
        return codificador.encode(JwtEncoderParameters.from(JwsHeader.with(JwtConfig.ALGORITMO).type("JWT").build(),
                claims)).getTokenValue();
    }

    private String base64Url(String texto) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(texto.getBytes(StandardCharsets.UTF_8));
    }
}

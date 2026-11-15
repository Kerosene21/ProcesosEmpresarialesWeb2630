package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;

import co.edu.javeriana.procesosempresariales.config.JwtConfig;
import co.edu.javeriana.procesosempresariales.config.JwtProperties;
import co.edu.javeriana.procesosempresariales.domain.FamiliaRefresco;
import co.edu.javeriana.procesosempresariales.domain.MotivoRevocacion;
import co.edu.javeriana.procesosempresariales.domain.RefreshToken;
import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.dto.CrearUsuarioDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.repository.FamiliaRefrescoRepository;
import co.edu.javeriana.procesosempresariales.repository.ProcesoRepository;
import co.edu.javeriana.procesosempresariales.repository.RefreshTokenRepository;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;
import co.edu.javeriana.procesosempresariales.service.CodigoRefresco;
import co.edu.javeriana.procesosempresariales.service.EmpresaService;
import co.edu.javeriana.procesosempresariales.service.TokenAccesoService;
import co.edu.javeriana.procesosempresariales.service.UsuarioService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AutenticacionJwtIntegracionTest {

    private static final String RUTA_CSRF = "/api/auth/csrf";
    private static final String RUTA_LOGIN = "/api/auth/login";
    private static final String RUTA_REFRESH = "/api/auth/refresh";
    private static final String RUTA_LOGOUT = "/api/auth/logout";
    private static final String RUTA_ME = "/api/auth/me";
    private static final String RUTA_PROCESOS = "/api/procesos";
    private static final String RUTA_USUARIOS = "/api/usuarios";
    private static final String COOKIE_CSRF = "XSRF-TOKEN";
    private static final String CABECERA_CSRF = "X-XSRF-TOKEN";
    private static final String COOKIE_REFRESCO = "PE_REFRESH";
    private static final String ADMIN_ALPES = "admin@alpes-jwt.com";
    private static final String EDITOR_ALPES = "editor@alpes-jwt.com";
    private static final String LECTURA_ALPES = "lectura@alpes-jwt.com";
    private static final String ADMIN_ANDES = "admin@andes-jwt.com";
    private static final String PASSWORD = "Clave-Jwt-2026";
    private static final String CODIGO = "$.codigo";
    private static final String NO_AUTORIZADO = "USUARIO_NO_AUTORIZADO";
    private static final String SIN_PERMISO = "USUARIO_SIN_PERMISO";
    private static final String SESION_EXPIRADA = "SESION_EXPIRADA";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private ProcesoRepository procesoRepository;

    @Autowired
    private FamiliaRefrescoRepository familiaRefrescoRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private CodigoRefresco codigoRefresco;

    @Autowired
    private TokenAccesoService tokenAccesoService;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    @BeforeEach
    void preparar() {
        empresaService.registrar(new RegistroEmpresaDto("Alpes JWT", "905000001-1", ADMIN_ALPES, PASSWORD));
        usuarioService.crear(new CrearUsuarioDto(EDITOR_ALPES, PASSWORD, RolUsuario.EDITOR), ADMIN_ALPES);
        usuarioService.crear(new CrearUsuarioDto(LECTURA_ALPES, PASSWORD, RolUsuario.SOLO_LECTURA), ADMIN_ALPES);
        empresaService.registrar(new RegistroEmpresaDto("Andes JWT", "905000002-2", ADMIN_ANDES, PASSWORD));
    }

    private ClientePruebaJwt clienteJwt() {
        return new ClientePruebaJwt(mockMvc);
    }

    private Cookie tokenCsrf() throws Exception {
        return clienteJwt().tokenCsrf();
    }

    private MockHttpServletRequestBuilder conCsrf(MockHttpServletRequestBuilder peticion, Cookie... cookies)
            throws Exception {
        return clienteJwt().conCsrf(peticion, cookies);
    }

    private String credenciales(String correo, String password) {
        return ClientePruebaJwt.credenciales(correo, password);
    }

    private MvcResult intentarLogin(String correo, String password) throws Exception {
        return clienteJwt().intentarLogin(correo, password);
    }

    private AccesoJwt sesion(MvcResult resultado) throws Exception {
        return clienteJwt().acceso(resultado);
    }

    private AccesoJwt login(String correo) throws Exception {
        return clienteJwt().iniciarSesion(correo, PASSWORD);
    }

    private MvcResult intentarRefrescar(Cookie refresco) throws Exception {
        return clienteJwt().intentarRefrescar(refresco);
    }

    private String proceso(String nombre) {
        return "{\"nombre\":\"" + nombre + "\",\"descripcion\":\"Proceso de prueba\",\"categoria\":\"General\"}";
    }

    private long crearProceso(AccesoJwt sesion, String nombre) throws Exception {
        return idDe(mockMvc.perform(post(RUTA_PROCESOS)
                .header(HttpHeaders.AUTHORIZATION, sesion.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(proceso(nombre)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private long idDe(MvcResult resultado) throws Exception {
        return ((Number) JsonPath.read(resultado.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private UUID familiaDe(AccesoJwt sesion) {
        return tokenAccesoService.familiaDe(tokenAccesoService.decodificar(sesion.token()));
    }

    private FamiliaRefresco familia(UUID id) {
        return familiaRefrescoRepository.findById(id).orElseThrow();
    }

    private String firmar(JwtClaimsSet claims) {
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(JwtConfig.ALGORITMO).type("JWT").build(),
                claims)).getTokenValue();
    }

    private JwtClaimsSet.Builder claims(String correo, UUID familia, Instant emitidoEn) {
        return JwtClaimsSet.builder()
                .issuer(jwtProperties.emisor())
                .audience(List.of(jwtProperties.audiencia()))
                .subject(correo)
                .id(UUID.randomUUID().toString())
                .issuedAt(emitidoEn)
                .expiresAt(emitidoEn.plus(Duration.ofMinutes(15)))
                .claim(JwtConfig.CLAIM_FAMILIA, familia.toString());
    }

    private MockHttpSession sesionDeNavegador(String correo) throws Exception {
        return clienteJwt().sesionDeNavegador(correo, PASSWORD);
    }

    private long idDe(String correo) {
        return usuarioRepository.findByUsername(correo).orElseThrow().getId();
    }

    private long familiasDe(String correo) {
        return familiaRefrescoRepository.findAll().stream()
                .filter(familia -> correo.equals(familia.getUsuario().getUsername()))
                .count();
    }

    @Test
    void elEndpointCsrfEmiteUnaCookieLegibleParaLaSpaSinCrearSesion() throws Exception {
        MvcResult resultado = mockMvc.perform(get(RUTA_CSRF))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists(COOKIE_CSRF))
                .andExpect(cookie().path(COOKIE_CSRF, "/"))
                .andExpect(cookie().httpOnly(COOKIE_CSRF, false))
                .andReturn();

        assertThat(resultado.getRequest().getSession(false)).isNull();
    }

    @Test
    void elLoginCorrectoEntregaElTokenDeAccesoYUnaCookieDeRefrescoSegura() throws Exception {
        MvcResult resultado = mockMvc.perform(conCsrf(post(RUTA_LOGIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(credenciales(" Editor@Alpes-JWT.com ", PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoToken").value("Bearer"))
                .andExpect(jsonPath("$.expiraEnSegundos").value(900))
                .andExpect(jsonPath("$.usuario.autenticado").value(true))
                .andExpect(jsonPath("$.usuario.correo").value(EDITOR_ALPES))
                .andExpect(jsonPath("$.usuario.rol").value("EDITOR"))
                .andExpect(jsonPath("$.usuario.empresaNombre").value("Alpes JWT"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(cookie().httpOnly(COOKIE_REFRESCO, true))
                .andExpect(cookie().secure(COOKIE_REFRESCO, true))
                .andExpect(cookie().path(COOKIE_REFRESCO, "/api/auth"))
                .andExpect(cookie().sameSite(COOKIE_REFRESCO, "Strict"))
                .andExpect(cookie().maxAge(COOKIE_REFRESCO, (int) Duration.ofDays(7).toSeconds()))
                .andExpect(cookie().doesNotExist("JSESSIONID"))
                .andReturn();

        AccesoJwt sesion = sesion(resultado);
        RefreshToken guardado = refreshTokenRepository
                .buscarParaRotar(codigoRefresco.hash(sesion.refresco().getValue())).orElseThrow();
        assertThat(resultado.getRequest().getSession(false)).isNull();
        assertThat(guardado.getTokenHash()).isNotEqualTo(sesion.refresco().getValue());
        assertThat(guardado.getFamilia().getId()).isEqualTo(familiaDe(sesion));
        assertThat(tokenAccesoService.decodificar(sesion.token()).getSubject()).isEqualTo(EDITOR_ALPES);
    }

    @Test
    void credencialesIncorrectasUsuarioInexistenteODesactivadoRecibenElMismoError() throws Exception {
        usuarioService.desactivar(idDe(LECTURA_ALPES), ADMIN_ALPES);

        for (MvcResult resultado : List.of(intentarLogin(EDITOR_ALPES, "Otra-Clave-2026"),
                intentarLogin("nadie@alpes-jwt.com", PASSWORD), intentarLogin(LECTURA_ALPES, PASSWORD))) {
            assertThat(resultado.getResponse().getStatus()).isEqualTo(401);
            assertThat(resultado.getResponse().getContentAsString())
                    .contains("CREDENCIALES_INVALIDAS", "Correo o contraseña incorrectos");
            assertThat(resultado.getResponse().getCookie(COOKIE_REFRESCO)).isNull();
        }
        assertThat(familiasDe(EDITOR_ALPES)).isZero();
        assertThat(familiasDe(LECTURA_ALPES)).isZero();
    }

    @Test
    void unLoginConDatosIncompletosSeRechaza() throws Exception {
        mockMvc.perform(conCsrf(post(RUTA_LOGIN))
                .contentType(MediaType.APPLICATION_JSON)
                .content(credenciales(" ", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath(CODIGO).value("DATOS_NO_VALIDOS"));
    }

    @Test
    void elLoginExigeUnTokenCsrfValido() throws Exception {
        Cookie token = tokenCsrf();

        mockMvc.perform(post(RUTA_LOGIN).contentType(MediaType.APPLICATION_JSON)
                .content(credenciales(EDITOR_ALPES, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value(SIN_PERMISO));
        mockMvc.perform(post(RUTA_LOGIN).cookie(token).header(CABECERA_CSRF, "token-falso")
                .contentType(MediaType.APPLICATION_JSON)
                .content(credenciales(EDITOR_ALPES, PASSWORD)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(RUTA_LOGIN).with(login(ADMIN_ALPES))
                .contentType(MediaType.APPLICATION_JSON)
                .content(credenciales(EDITOR_ALPES, PASSWORD)))
                .andExpect(status().isForbidden());

        assertThat(familiasDe(EDITOR_ALPES)).isZero();
    }

    @Test
    void unBearerValidoAutenticaConElCorreoComoPrincipalSinCrearSesion() throws Exception {
        AccesoJwt editor = login(EDITOR_ALPES);

        MvcResult consulta = mockMvc.perform(get(RUTA_PROCESOS).header(HttpHeaders.AUTHORIZATION, editor.bearer()))
                .andExpect(status().isOk())
                .andReturn();
        mockMvc.perform(get(RUTA_ME).header(HttpHeaders.AUTHORIZATION, editor.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value(EDITOR_ALPES))
                .andExpect(jsonPath("$.rol").value("EDITOR"));
        long id = crearProceso(editor, "Compras JWT");

        assertThat(consulta.getRequest().getSession(false)).isNull();
        assertThat(procesoRepository.findById(id).orElseThrow().getEmpresa().getNombre()).isEqualTo("Alpes JWT");
    }

    @Test
    void lasEscriturasConBearerYaNoExigenCsrfNiCreanSesion() throws Exception {
        AccesoJwt editor = login(EDITOR_ALPES);

        MvcResult creacion = mockMvc.perform(post(RUTA_PROCESOS).with(editor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(proceso("Sin CSRF")))
                .andExpect(status().isCreated())
                .andReturn();
        mockMvc.perform(post(RUTA_PROCESOS).with(editor).header(CABECERA_CSRF, "token-falso")
                .contentType(MediaType.APPLICATION_JSON)
                .content(proceso("Con CSRF falso")))
                .andExpect(status().isCreated());
        long id = idDe(creacion);
        MvcResult edicion = mockMvc.perform(put(RUTA_PROCESOS + "/" + id).with(editor)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Sin CSRF editado\",\"descripcion\":\"Cambio\",\"categoria\":\"General\","
                        + "\"estado\":\"BORRADOR\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult eliminacion = mockMvc.perform(delete(RUTA_PROCESOS + "/" + id).with(login(ADMIN_ALPES)))
                .andExpect(status().isNoContent())
                .andReturn();

        for (MvcResult resultado : List.of(creacion, edicion, eliminacion)) {
            assertThat(resultado.getRequest().getSession(false)).isNull();
            assertThat(resultado.getResponse().getCookie("JSESSIONID")).isNull();
            assertThat(resultado.getResponse().getCookie(COOKIE_CSRF)).isNull();
        }
        assertThat(procesoRepository.findById(id).orElseThrow().isEliminado()).isTrue();
        assertThat(procesoRepository.findAll()).extracting(p -> p.getNombre()).contains("Con CSRF falso");
    }

    @Test
    void unBearerExpiradoManipuladoOFirmadoConOtraClaveSeRechaza() throws Exception {
        AccesoJwt editor = login(EDITOR_ALPES);
        String expirado = firmar(claims(EDITOR_ALPES, familiaDe(editor), Instant.now().minus(Duration.ofHours(1)))
                .build());
        String[] partes = editor.token().split("\\.");
        String manipulado = partes[0] + "." + partes[1] + "x." + partes[2];

        for (String token : List.of(expirado, manipulado, "no-es-un-jwt")) {
            mockMvc.perform(get(RUTA_PROCESOS).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath(CODIGO).value(NO_AUTORIZADO))
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, not(containsString("resource_metadata"))));
        }
        mockMvc.perform(get(RUTA_ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + expirado))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void losClaimsAgregadosAlTokenNoOtorganEmpresaNiRol() throws Exception {
        AccesoJwt editor = login(EDITOR_ALPES);
        long empresaAndes = usuarioRepository.findByUsername(ADMIN_ANDES).orElseThrow().getEmpresa().getId();
        String forjado = firmar(claims(EDITOR_ALPES, familiaDe(editor), Instant.now())
                .claim("rol", "ADMINISTRADOR")
                .claim("roles", List.of("ROLE_ADMINISTRADOR"))
                .claim("scope", "ADMINISTRADOR")
                .claim("empresaId", empresaAndes)
                .build());

        mockMvc.perform(get(RUTA_USUARIOS).header(HttpHeaders.AUTHORIZATION, "Bearer " + forjado))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value(SIN_PERMISO));
        mockMvc.perform(get(RUTA_ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + forjado))
                .andExpect(jsonPath("$.rol").value("EDITOR"))
                .andExpect(jsonPath("$.empresaNombre").value("Alpes JWT"));
    }

    @Test
    void elRefreshRotaLaCookieYElTokenAnteriorQuedaUsado() throws Exception {
        AccesoJwt inicial = login(EDITOR_ALPES);

        MvcResult resultado = mockMvc.perform(conCsrf(post(RUTA_REFRESH), inicial.refresco()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.correo").value(EDITOR_ALPES))
                .andExpect(cookie().httpOnly(COOKIE_REFRESCO, true))
                .andExpect(cookie().path(COOKIE_REFRESCO, "/api/auth"))
                .andReturn();
        AccesoJwt renovada = sesion(resultado);

        assertThat(renovada.refresco().getValue()).isNotEqualTo(inicial.refresco().getValue());
        assertThat(familiaDe(renovada)).isEqualTo(familiaDe(inicial));
        assertThat(refreshTokenRepository.buscarParaRotar(codigoRefresco.hash(inicial.refresco().getValue()))
                .orElseThrow().getUsadoEn()).isNotNull();
        mockMvc.perform(get(RUTA_PROCESOS).header(HttpHeaders.AUTHORIZATION, renovada.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void elRefreshExigeCsrfYUnaCookie() throws Exception {
        AccesoJwt inicial = login(EDITOR_ALPES);
        Cookie token = tokenCsrf();

        mockMvc.perform(post(RUTA_REFRESH).cookie(inicial.refresco()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(RUTA_REFRESH).cookie(inicial.refresco()).with(inicial))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(RUTA_REFRESH).cookie(inicial.refresco(), token).header(CABECERA_CSRF, "falso"))
                .andExpect(status().isForbidden());
        mockMvc.perform(conCsrf(post(RUTA_REFRESH)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath(CODIGO).value(SESION_EXPIRADA))
                .andExpect(cookie().maxAge(COOKIE_REFRESCO, 0));

        assertThat(refreshTokenRepository.buscarParaRotar(codigoRefresco.hash(inicial.refresco().getValue()))
                .orElseThrow().getUsadoEn()).isNull();
    }

    @Test
    void dentroDeLaGraciaElTokenAnteriorSoloObtieneUnAccesoSinNuevaCookie() throws Exception {
        AccesoJwt inicial = login(EDITOR_ALPES);
        AccesoJwt renovada = sesion(intentarRefrescar(inicial.refresco()));

        MvcResult carrera = mockMvc.perform(conCsrf(post(RUTA_REFRESH), inicial.refresco()))
                .andExpect(status().isOk())
                .andExpect(cookie().doesNotExist(COOKIE_REFRESCO))
                .andReturn();
        String acceso = JsonPath.read(carrera.getResponse().getContentAsString(), "$.tokenAcceso");
        int segundos = JsonPath.read(carrera.getResponse().getContentAsString(), "$.expiraEnSegundos");

        assertThat(segundos).isLessThanOrEqualTo(900);
        assertThat(familia(familiaDe(inicial)).getRevocadaEn()).isNull();
        mockMvc.perform(get(RUTA_PROCESOS).header(HttpHeaders.AUTHORIZATION, "Bearer " + acceso))
                .andExpect(status().isOk());
        assertThat(sesion(intentarRefrescar(renovada.refresco())).refresco()).isNotNull();
    }

    @Test
    void reutilizarUnRefreshRotadoFueraDeLaGraciaRevocaTodaLaSesion() throws Exception {
        AccesoJwt inicial = login(EDITOR_ALPES);
        AccesoJwt renovada = sesion(intentarRefrescar(inicial.refresco()));
        RefreshToken anterior = refreshTokenRepository
                .buscarParaRotar(codigoRefresco.hash(inicial.refresco().getValue())).orElseThrow();
        anterior.setUsadoEn(anterior.getUsadoEn().minusSeconds(30));
        refreshTokenRepository.saveAndFlush(anterior);

        mockMvc.perform(conCsrf(post(RUTA_REFRESH), inicial.refresco()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath(CODIGO).value(SESION_EXPIRADA))
                .andExpect(cookie().maxAge(COOKIE_REFRESCO, 0));

        FamiliaRefresco revocada = familia(familiaDe(inicial));
        assertThat(revocada.getMotivoRevocacion()).isEqualTo(MotivoRevocacion.REUTILIZACION_DETECTADA);
        assertThat(intentarRefrescar(renovada.refresco()).getResponse().getStatus()).isEqualTo(401);
        mockMvc.perform(get(RUTA_PROCESOS).header(HttpHeaders.AUTHORIZATION, renovada.bearer()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elLogoutRevocaLaSesionDeInmediatoYEliminaLaCookie() throws Exception {
        AccesoJwt editor = login(EDITOR_ALPES);

        mockMvc.perform(conCsrf(post(RUTA_LOGOUT), editor.refresco()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().value(COOKIE_REFRESCO, ""))
                .andExpect(cookie().maxAge(COOKIE_REFRESCO, 0))
                .andExpect(cookie().path(COOKIE_REFRESCO, "/api/auth"));

        assertThat(familia(familiaDe(editor)).getMotivoRevocacion()).isEqualTo(MotivoRevocacion.CIERRE_SESION);
        mockMvc.perform(get(RUTA_PROCESOS).header(HttpHeaders.AUTHORIZATION, editor.bearer()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(RUTA_ME).header(HttpHeaders.AUTHORIZATION, editor.bearer()))
                .andExpect(status().isUnauthorized());
        assertThat(intentarRefrescar(editor.refresco()).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void elLogoutFuncionaConUnAccesoExpiradoPeroExigeCsrf() throws Exception {
        AccesoJwt editor = login(EDITOR_ALPES);
        String expirado = firmar(claims(EDITOR_ALPES, familiaDe(editor), Instant.now().minus(Duration.ofHours(1)))
                .build());

        mockMvc.perform(post(RUTA_LOGOUT).cookie(editor.refresco()).header(HttpHeaders.AUTHORIZATION, "Bearer " + expirado))
                .andExpect(status().isForbidden());
        assertThat(familia(familiaDe(editor)).getRevocadaEn()).isNull();

        mockMvc.perform(conCsrf(post(RUTA_LOGOUT), editor.refresco())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + expirado))
                .andExpect(status().isNoContent());
        assertThat(familia(familiaDe(editor)).getRevocadaEn()).isNotNull();
        mockMvc.perform(conCsrf(post(RUTA_LOGOUT)))
                .andExpect(status().isNoContent());
    }

    @Test
    void desactivarAUnUsuarioBloqueaDeInmediatoSuAccesoYSuRefresh() throws Exception {
        AccesoJwt administrador = login(ADMIN_ALPES);
        AccesoJwt editor = login(EDITOR_ALPES);
        AccesoJwt otraPestana = login(EDITOR_ALPES);

        mockMvc.perform(post(RUTA_USUARIOS + "/" + idDe(EDITOR_ALPES) + "/desactivacion")
                .header(HttpHeaders.AUTHORIZATION, administrador.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));

        mockMvc.perform(get(RUTA_PROCESOS).header(HttpHeaders.AUTHORIZATION, editor.bearer()))
                .andExpect(status().isUnauthorized());
        assertThat(intentarRefrescar(editor.refresco()).getResponse().getStatus()).isEqualTo(401);
        assertThat(familia(familiaDe(editor)).getMotivoRevocacion()).isEqualTo(MotivoRevocacion.USUARIO_DESACTIVADO);
        assertThat(familia(familiaDe(otraPestana)).getMotivoRevocacion())
                .isEqualTo(MotivoRevocacion.USUARIO_DESACTIVADO);
        assertThat(familia(familiaDe(administrador)).getRevocadaEn()).isNull();
        mockMvc.perform(get(RUTA_PROCESOS).header(HttpHeaders.AUTHORIZATION, administrador.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void unCambioDeRolSeAplicaEnLaSiguientePeticionDelMismoToken() throws Exception {
        AccesoJwt administrador = login(ADMIN_ALPES);
        AccesoJwt editor = login(EDITOR_ALPES);
        crearProceso(editor, "Antes del cambio");

        mockMvc.perform(put(RUTA_USUARIOS + "/" + idDe(EDITOR_ALPES) + "/rol")
                .header(HttpHeaders.AUTHORIZATION, administrador.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rol\":\"SOLO_LECTURA\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(RUTA_PROCESOS)
                .header(HttpHeaders.AUTHORIZATION, editor.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(proceso("Despues del cambio")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value(SIN_PERMISO));
        mockMvc.perform(get(RUTA_ME).header(HttpHeaders.AUTHORIZATION, editor.bearer()))
                .andExpect(jsonPath("$.rol").value("SOLO_LECTURA"));
        mockMvc.perform(conCsrf(post(RUTA_REFRESH), editor.refresco()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario.rol").value("SOLO_LECTURA"));
    }

    @Test
    void conJwtCadaEmpresaSoloVeYModificaSusRecursos() throws Exception {
        AccesoJwt alpes = login(EDITOR_ALPES);
        AccesoJwt andes = login(ADMIN_ANDES);
        long deAndes = crearProceso(andes, "Exclusivo Andes");

        mockMvc.perform(get(RUTA_PROCESOS + "/" + deAndes).header(HttpHeaders.AUTHORIZATION, alpes.bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value(SIN_PERMISO));
        mockMvc.perform(put(RUTA_PROCESOS + "/" + deAndes)
                .header(HttpHeaders.AUTHORIZATION, alpes.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Intento ajeno\",\"descripcion\":\"Cambio\",\"categoria\":\"General\","
                        + "\"estado\":\"BORRADOR\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value(SIN_PERMISO));
        mockMvc.perform(get(RUTA_PROCESOS).header(HttpHeaders.AUTHORIZATION, alpes.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].nombre", not(org.hamcrest.Matchers.hasItem("Exclusivo Andes"))));
        mockMvc.perform(get(RUTA_USUARIOS).header(HttpHeaders.AUTHORIZATION, andes.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].correo", org.hamcrest.Matchers.contains(ADMIN_ANDES)));

        assertThat(procesoRepository.findById(deAndes).orElseThrow().getNombre()).isEqualTo("Exclusivo Andes");
    }

    @Test
    void conBearerValidoYLaSesionWebDeOtroUsuarioSoloCuentaElUsuarioDelBearer() throws Exception {
        MockHttpSession navegadorAndes = sesionDeNavegador(ADMIN_ANDES);
        AccesoJwt lectura = login(LECTURA_ALPES);
        AccesoJwt editor = login(EDITOR_ALPES);

        mockMvc.perform(get(RUTA_USUARIOS).session(navegadorAndes).with(lectura))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value(SIN_PERMISO));
        mockMvc.perform(get(RUTA_ME).session(navegadorAndes).with(lectura))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value(LECTURA_ALPES))
                .andExpect(jsonPath("$.empresaNombre").value("Alpes JWT"));
        mockMvc.perform(post(RUTA_PROCESOS).session(navegadorAndes).with(lectura)
                .contentType(MediaType.APPLICATION_JSON)
                .content(proceso("Escrito por lectura")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value(SIN_PERMISO));
        long id = idDe(mockMvc.perform(post(RUTA_PROCESOS).session(navegadorAndes).with(editor)
                .contentType(MediaType.APPLICATION_JSON)
                .content(proceso("Escrito por editor")))
                .andExpect(status().isCreated())
                .andReturn());

        assertThat(procesoRepository.findById(id).orElseThrow().getEmpresa().getNombre()).isEqualTo("Alpes JWT");
        assertThat(procesoRepository.findAll()).noneMatch(p -> "Escrito por lectura".equals(p.getNombre()));
        SecurityContext guardado = (SecurityContext) navegadorAndes
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(guardado.getAuthentication().getName()).isEqualTo(ADMIN_ANDES);
    }

    @Test
    void unaSesionWebValidaSinBearerNoAutenticaLaApi() throws Exception {
        MockHttpSession navegador = sesionDeNavegador(ADMIN_ALPES);

        for (String ruta : List.of(RUTA_PROCESOS, RUTA_USUARIOS, RUTA_ME, "/api/empresas/actual")) {
            mockMvc.perform(get(ruta).session(navegador))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath(CODIGO).value(NO_AUTORIZADO))
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        }
        mockMvc.perform(post(RUTA_PROCESOS).session(navegador).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(proceso("Con sesion web")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(conCsrf(put(RUTA_USUARIOS + "/" + idDe(EDITOR_ALPES) + "/rol")).session(navegador)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rol\":\"ADMINISTRADOR\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(procesoRepository.findAll()).noneMatch(p -> "Con sesion web".equals(p.getNombre()));
        assertThat(usuarioRepository.findByUsername(EDITOR_ALPES).orElseThrow().getRol())
                .isEqualTo(RolUsuario.EDITOR);
        assertThat(navegador.isInvalid()).isFalse();
        mockMvc.perform(get("/procesos").session(navegador))
                .andExpect(status().isOk());
    }

    @Test
    void laRutaAntiguaDeSesionYaNoExisteNiAbreSesiones() throws Exception {
        MockHttpSession navegador = sesionDeNavegador(ADMIN_ALPES);

        mockMvc.perform(get("/api/sesion"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/sesion").session(navegador))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/sesion").with(login(ADMIN_ALPES)))
                .andExpect(status().isNotFound());
        MvcResult intento = mockMvc.perform(conCsrf(post("/api/sesion"))
                .param("correo", ADMIN_ALPES)
                .param("password", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andReturn();
        mockMvc.perform(conCsrf(delete("/api/sesion")).session(navegador))
                .andExpect(status().isUnauthorized());

        assertThat(intento.getRequest().getSession(false)).isNull();
        assertThat(navegador.isInvalid()).isFalse();
    }

    @Test
    void unBearerInvalidoNuncaRecurreALaSesionExistente() throws Exception {
        MockHttpSession navegador = sesionDeNavegador(ADMIN_ALPES);

        mockMvc.perform(get(RUTA_USUARIOS).session(navegador).header(HttpHeaders.AUTHORIZATION, "Bearer invalido"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath(CODIGO).value(NO_AUTORIZADO))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")));
        mockMvc.perform(post(RUTA_PROCESOS).session(navegador)
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalido")
                .contentType(MediaType.APPLICATION_JSON)
                .content(proceso("Con bearer invalido")))
                .andExpect(status().isUnauthorized());

        assertThat(procesoRepository.findAll()).noneMatch(p -> p.getNombre().startsWith("Con bearer"));
        mockMvc.perform(get("/procesos").session(navegador))
                .andExpect(status().isOk());
    }

    @Test
    void unBearerRevocadoOExpiradoRecibe401AunqueExistaUnaSesionWeb() throws Exception {
        MockHttpSession navegador = sesionDeNavegador(ADMIN_ALPES);
        AccesoJwt revocado = login(ADMIN_ALPES);
        String expirado = firmar(claims(ADMIN_ALPES, familiaDe(login(ADMIN_ALPES)),
                Instant.now().minus(Duration.ofHours(1))).build());
        mockMvc.perform(conCsrf(post(RUTA_LOGOUT), revocado.refresco()))
                .andExpect(status().isNoContent());

        for (String bearer : List.of(revocado.bearer(), "Bearer " + expirado)) {
            mockMvc.perform(get(RUTA_USUARIOS).session(navegador).header(HttpHeaders.AUTHORIZATION, bearer))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath(CODIGO).value(NO_AUTORIZADO));
            mockMvc.perform(post(RUTA_PROCESOS).session(navegador)
                    .header(HttpHeaders.AUTHORIZATION, bearer)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(proceso("Con bearer no vigente")))
                    .andExpect(status().isUnauthorized());
        }

        assertThat(procesoRepository.findAll()).noneMatch(p -> "Con bearer no vigente".equals(p.getNombre()));
    }

    @Test
    void unAscensoDeRolTambienSeAplicaAlMismoTokenSinEmitirUnoNuevo() throws Exception {
        AccesoJwt administrador = login(ADMIN_ALPES);
        AccesoJwt lectura = login(LECTURA_ALPES);
        mockMvc.perform(get(RUTA_USUARIOS).with(lectura))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath(CODIGO).value(SIN_PERMISO));

        mockMvc.perform(put(RUTA_USUARIOS + "/" + idDe(LECTURA_ALPES) + "/rol").with(administrador)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rol\":\"ADMINISTRADOR\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(RUTA_USUARIOS).with(lectura))
                .andExpect(status().isOk());
        mockMvc.perform(get(RUTA_ME).with(lectura))
                .andExpect(jsonPath("$.rol").value("ADMINISTRADOR"));
    }

    @Test
    void lasVistasThymeleafNoAceptanBearer() throws Exception {
        AccesoJwt editor = login(EDITOR_ALPES);

        mockMvc.perform(get("/procesos").header(HttpHeaders.AUTHORIZATION, editor.bearer()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}

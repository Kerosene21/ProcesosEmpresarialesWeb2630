package co.edu.javeriana.procesosempresariales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.domain.Usuario;
import co.edu.javeriana.procesosempresariales.dto.EmpresaRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.repository.UsuarioRepository;
import co.edu.javeriana.procesosempresariales.service.EmpresaService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RegistroYLoginIntegracionTest {

    private static final String CORREO_ALPES = "admin@alpes-integracion.com";
    private static final String CORREO_ANDES = "admin@andes-integracion.com";
    private static final String PASSWORD = "Clave-Integracion-2026";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmpresaService empresaService;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private EmpresaRespuestaDto registrarAlpes() {
        return empresaService.registrar(
                new RegistroEmpresaDto("Alpes Integracion", "901000001-1", CORREO_ALPES, PASSWORD));
    }

    private EmpresaRespuestaDto registrarAndes() {
        return empresaService.registrar(
                new RegistroEmpresaDto("Andes Integracion", "901000002-2", CORREO_ANDES, PASSWORD));
    }

    private MockHttpSession iniciarSesion(String correo) throws Exception {
        MvcResult resultado = mockMvc.perform(formLogin().user(correo).password(PASSWORD))
                .andExpect(authenticated().withUsername(correo))
                .andReturn();
        return (MockHttpSession) resultado.getRequest().getSession(false);
    }

    @Test
    void lasCredencialesCreadasEnElRegistroPermitenIniciarSesion() throws Exception {
        registrarAlpes();

        mockMvc.perform(formLogin().user(CORREO_ALPES).password(PASSWORD))
                .andExpect(authenticated().withUsername(CORREO_ALPES))
                .andExpect(redirectedUrl("/empresas"));
    }

    @Test
    void elAdministradorInicialQuedaPersistidoConHashActivoYRolAdministrador() {
        registrarAlpes();

        Usuario administrador = usuarioRepository.findByUsername(CORREO_ALPES).orElseThrow();
        assertThat(administrador.getPassword()).isNotEqualTo(PASSWORD).startsWith("$2");
        assertThat(administrador.isActivo()).isTrue();
        assertThat(administrador.getRol()).isEqualTo(RolUsuario.ADMINISTRADOR);
        assertThat(administrador.getEmpresa().getNit()).isEqualTo("901000001-1");
    }

    @Test
    void unaContrasenaIncorrectaNoPermiteIniciarSesion() throws Exception {
        registrarAlpes();

        mockMvc.perform(formLogin().user(CORREO_ALPES).password("otra-contrasena"))
                .andExpect(unauthenticated())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void elUsuarioAutenticadoVeSuPropiaEmpresa() throws Exception {
        EmpresaRespuestaDto alpes = registrarAlpes();
        MockHttpSession sesion = iniciarSesion(CORREO_ALPES);

        mockMvc.perform(get("/empresas/" + alpes.getId()).session(sesion))
                .andExpect(status().isOk())
                .andExpect(view().name("empresas/detalle"));
    }

    @Test
    void unUsuarioDeLaEmpresaANoPuedeConsultarLaEmpresaB() throws Exception {
        registrarAlpes();
        EmpresaRespuestaDto andes = registrarAndes();
        MockHttpSession sesion = iniciarSesion(CORREO_ALPES);

        mockMvc.perform(get("/empresas/" + andes.getId()).session(sesion))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error/problema"));
    }

    @Test
    void elListadoSoloMuestraLaEmpresaDelUsuarioAutenticado() throws Exception {
        registrarAlpes();
        registrarAndes();
        MockHttpSession sesion = iniciarSesion(CORREO_ALPES);

        MvcResult resultado = mockMvc.perform(get("/empresas").session(sesion))
                .andExpect(status().isOk())
                .andExpect(view().name("empresas/lista"))
                .andReturn();

        @SuppressWarnings("unchecked")
        java.util.List<EmpresaRespuestaDto> empresas =
                (java.util.List<EmpresaRespuestaDto>) resultado.getModelAndView().getModel().get("empresas");
        assertThat(empresas).hasSize(1);
        assertThat(empresas.get(0).getNit()).isEqualTo("901000001-1");
    }
}

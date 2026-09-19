package co.edu.javeriana.procesosempresariales.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.javeriana.procesosempresariales.config.SecurityConfig;
import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EmpresaRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RegistroEmpresaDto;
import co.edu.javeriana.procesosempresariales.service.EmpresaService;
import co.edu.javeriana.procesosempresariales.service.ProcesoService;

@WebMvcTest(controllers = { AutenticacionController.class, EmpresaController.class, ProcesoController.class,
        ProcesoRestController.class })
@Import(SecurityConfig.class)
class SeguridadRutasTest {

    private static final String USERNAME = "admin@alpes.com";
    private static final String REDIRECCION_LOGIN = "/login";

    private static final String JSON_CREACION = """
            {"nombre":"Ventas","descripcion":"Proceso comercial","categoria":"Comercial"}
            """;

    private static final String JSON_EDICION = """
            {"nombre":"Ventas Corporativas","descripcion":"Descripcion actualizada",
             "categoria":"Operaciones","estado":"PUBLICADO"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmpresaService empresaService;

    @MockitoBean
    private ProcesoService procesoService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private ProcesoRespuestaDto proceso() {
        ProcesoRespuestaDto proceso = new ProcesoRespuestaDto();
        proceso.setId(5L);
        proceso.setNombre("Ventas");
        proceso.setDescripcion("Proceso comercial");
        proceso.setCategoria("Comercial");
        proceso.setEstado(EstadoProceso.BORRADOR);
        proceso.setPoolId(80L);
        return proceso;
    }

    private EmpresaRespuestaDto empresa() {
        EmpresaRespuestaDto empresa = new EmpresaRespuestaDto();
        empresa.setId(10L);
        empresa.setNombre("Alpes Logistica");
        empresa.setNit("900123456-7");
        empresa.setCorreoContacto(USERNAME);
        return empresa;
    }

    @Test
    void laPaginaDeLoginEsAccesibleSinAutenticacion() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("autenticacion/login"));
    }

    @Test
    void losRecursosEstaticosDeLasVistasSonAccesiblesSinAutenticacion() throws Exception {
        mockMvc.perform(get("/css/empresas.css")).andExpect(status().isOk());
        mockMvc.perform(get("/js/procesos.js")).andExpect(status().isOk());
    }

    @Test
    void elFormularioDeRegistroDeEmpresaEsAccesibleSinAutenticacion() throws Exception {
        mockMvc.perform(get("/empresas/nueva")).andExpect(status().isOk());
    }

    @Test
    void elRegistroDeEmpresaSePuedeEnviarSinAutenticacionPorqueEsElPuntoDeEntrada() throws Exception {
        when(empresaService.registrar(any(RegistroEmpresaDto.class))).thenReturn(empresa());

        mockMvc.perform(post("/empresas").with(csrf())
                .param("nombre", "Alpes Logistica")
                .param("nit", "900123456-7")
                .param("correoContacto", USERNAME)
                .param("passwordAdministrador", "Clave-Inicial-2026"))
                .andExpect(redirectedUrl("/empresas/10"));
    }

    @Test
    void elListadoDeEmpresasExigeAutenticacion() throws Exception {
        mockMvc.perform(get("/empresas"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(REDIRECCION_LOGIN));

        verify(empresaService, never()).listarVisiblesPara(anyString());
    }

    @Test
    void elDetalleDeEmpresaExigeAutenticacion() throws Exception {
        mockMvc.perform(get("/empresas/10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(REDIRECCION_LOGIN));

        verify(empresaService, never()).obtenerParaUsuario(anyLong(), anyString());
    }

    @Test
    void elFormularioDeCreacionDeProcesoExigeAutenticacion() throws Exception {
        mockMvc.perform(get("/procesos/nuevo"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(REDIRECCION_LOGIN));
    }

    @Test
    void elDetalleDeProcesoExigeAutenticacion() throws Exception {
        mockMvc.perform(get("/procesos/5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(REDIRECCION_LOGIN));

        verify(procesoService, never()).obtener(anyLong(), anyString());
    }

    @Test
    void elFormularioDeEdicionDeProcesoExigeAutenticacion() throws Exception {
        mockMvc.perform(get("/procesos/5/editar"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(REDIRECCION_LOGIN));

        verify(procesoService, never()).obtener(anyLong(), anyString());
    }

    @Test
    void crearUnProcesoDesdeElFormularioExigeAutenticacion() throws Exception {
        mockMvc.perform(post("/procesos").with(csrf())
                .param("nombre", "Ventas")
                .param("descripcion", "Proceso comercial")
                .param("categoria", "Comercial"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(REDIRECCION_LOGIN));

        verify(procesoService, never()).crear(any(CrearProcesoDto.class), anyString());
    }

    @Test
    void actualizarUnProcesoExigeAutenticacionAntesDeValidarElFormulario() throws Exception {
        mockMvc.perform(post("/procesos/5").with(csrf())
                .param("nombre", "")
                .param("descripcion", "")
                .param("categoria", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(REDIRECCION_LOGIN));

        verify(procesoService, never()).editar(anyLong(), any(EditarProcesoDto.class), anyString());
    }

    @Test
    void laApiDeCreacionDeProcesosResponde401EnJsonSinAutenticacion() throws Exception {
        mockMvc.perform(post("/api/procesos").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_CREACION))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("USUARIO_NO_AUTORIZADO"));

        verify(procesoService, never()).crear(any(CrearProcesoDto.class), anyString());
    }

    @Test
    void laApiDeEdicionDeProcesosResponde401EnJsonSinAutenticacion() throws Exception {
        mockMvc.perform(put("/api/procesos/5").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_EDICION))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("USUARIO_NO_AUTORIZADO"));

        verify(procesoService, never()).editar(anyLong(), any(EditarProcesoDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME)
    void elUsuarioAutenticadoLlegaAlControladorDeProcesosConSuPrincipalReal() throws Exception {
        when(procesoService.obtener(5L, USERNAME)).thenReturn(proceso());
        when(procesoService.puedeEditar(USERNAME)).thenReturn(true);

        mockMvc.perform(get("/procesos/5")).andExpect(status().isOk());

        verify(procesoService).obtener(5L, USERNAME);
    }

    @Test
    @WithMockUser(username = USERNAME)
    void elUsuarioAutenticadoLlegaAlControladorDeEmpresasConSuPrincipalReal() throws Exception {
        when(empresaService.obtenerParaUsuario(10L, USERNAME)).thenReturn(empresa());

        mockMvc.perform(get("/empresas/10")).andExpect(status().isOk());

        verify(empresaService).obtenerParaUsuario(10L, USERNAME);
    }

    @Test
    @WithMockUser(username = USERNAME)
    void elUsuarioAutenticadoLlegaALaApiConSuPrincipalReal() throws Exception {
        when(procesoService.crear(any(CrearProcesoDto.class), anyString())).thenReturn(proceso());

        mockMvc.perform(post("/api/procesos").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_CREACION))
                .andExpect(status().isCreated());

        verify(procesoService).crear(any(CrearProcesoDto.class), anyString());
    }

    @Test
    @WithMockUser(username = USERNAME)
    void unEnvioSinTokenCsrfSeRechazaAunqueElUsuarioEsteAutenticado() throws Exception {
        mockMvc.perform(post("/procesos")
                .param("nombre", "Ventas")
                .param("descripcion", "Proceso comercial")
                .param("categoria", "Comercial"))
                .andExpect(status().isForbidden());

        verify(procesoService, never()).crear(any(CrearProcesoDto.class), anyString());
    }
}

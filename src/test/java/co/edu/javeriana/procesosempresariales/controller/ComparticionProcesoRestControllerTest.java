package co.edu.javeriana.procesosempresariales.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import co.edu.javeriana.procesosempresariales.dto.EmpresaInvitadaDto;
import co.edu.javeriana.procesosempresariales.exception.ComparticionNoValidaException;
import co.edu.javeriana.procesosempresariales.exception.ProcesoYaCompartidoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.service.ComparticionProcesoService;

@ExtendWith(MockitoExtension.class)
class ComparticionProcesoRestControllerTest {

    private static final String USERNAME = "admin@alpes.com";
    private static final Principal PRINCIPAL = () -> USERNAME;
    private static final String RUTA = "/api/procesos/5/compartido-con";
    private static final String RUTA_EMPRESA = "/api/procesos/5/compartido-con/99";

    @Mock
    private ComparticionProcesoService comparticionProcesoService;

    private MockMvc mockMvc;

    @BeforeEach
    void inicializar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ComparticionProcesoRestController(comparticionProcesoService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void listarDevuelveLasEmpresasConLasQueSeComparte() throws Exception {
        when(comparticionProcesoService.listar(5L, USERNAME))
                .thenReturn(List.of(new EmpresaInvitadaDto(99L, "Andes Distribucion")));

        mockMvc.perform(get(RUTA).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].empresaId").value(99))
                .andExpect(jsonPath("$[0].nombre").value("Andes Distribucion"));
    }

    @Test
    void compartirDevuelve201ConLaEmpresaInvitada() throws Exception {
        when(comparticionProcesoService.compartir(5L, 99L, USERNAME))
                .thenReturn(new EmpresaInvitadaDto(99L, "Andes Distribucion"));

        mockMvc.perform(post(RUTA_EMPRESA).principal(PRINCIPAL))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.empresaId").value(99));
    }

    @Test
    void compartirConLaPropiaEmpresaDevuelve400() throws Exception {
        when(comparticionProcesoService.compartir(5L, 99L, USERNAME))
                .thenThrow(new ComparticionNoValidaException("El proceso ya pertenece a esa empresa"));

        mockMvc.perform(post(RUTA_EMPRESA).principal(PRINCIPAL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("COMPARTICION_NO_VALIDA"));
    }

    @Test
    void compartirDosVecesConLaMismaEmpresaDevuelve409() throws Exception {
        when(comparticionProcesoService.compartir(5L, 99L, USERNAME))
                .thenThrow(new ProcesoYaCompartidoException("El proceso ya está compartido con esa empresa"));

        mockMvc.perform(post(RUTA_EMPRESA).principal(PRINCIPAL))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PROCESO_YA_COMPARTIDO"));
    }

    @Test
    void compartirSinSerAdministradorDevuelve403() throws Exception {
        when(comparticionProcesoService.compartir(5L, 99L, USERNAME))
                .thenThrow(new UsuarioSinPermisoException("Solo un administrador puede compartir procesos"));

        mockMvc.perform(post(RUTA_EMPRESA).principal(PRINCIPAL))
                .andExpect(status().isForbidden());
    }

    @Test
    void compartirConUnaEmpresaInexistenteDevuelve404() throws Exception {
        when(comparticionProcesoService.compartir(5L, 99L, USERNAME))
                .thenThrow(new RecursoNoEncontradoException("La empresa no existe"));

        mockMvc.perform(post(RUTA_EMPRESA).principal(PRINCIPAL))
                .andExpect(status().isNotFound());
    }

    @Test
    void dejarDeCompartirDevuelve204() throws Exception {
        mockMvc.perform(delete(RUTA_EMPRESA).principal(PRINCIPAL))
                .andExpect(status().isNoContent());

        verify(comparticionProcesoService).dejarDeCompartir(5L, 99L, USERNAME);
    }
}

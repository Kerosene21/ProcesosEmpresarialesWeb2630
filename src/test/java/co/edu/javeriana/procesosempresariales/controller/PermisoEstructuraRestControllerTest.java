package co.edu.javeriana.procesosempresariales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import co.edu.javeriana.procesosempresariales.dto.ConfigurarPermisoEstructuraDto;
import co.edu.javeriana.procesosempresariales.dto.PermisoEstructuraDto;
import co.edu.javeriana.procesosempresariales.exception.PermisoEstructuraNoValidoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.service.PermisoEstructuraService;

@ExtendWith(MockitoExtension.class)
class PermisoEstructuraRestControllerTest {

    private static final String USERNAME = "admin@alpes.com";
    private static final Principal PRINCIPAL = () -> USERNAME;
    private static final String RUTA = "/api/procesos/5/permisos-estructura";

    private static final String JSON_PERMISOS = """
            {"crearPool":true,"editarPool":true,"eliminarPool":true,
             "crearLane":true,"editarLane":false,"eliminarLane":true}
            """;

    @Mock
    private PermisoEstructuraService permisoEstructuraService;

    private MockMvc mockMvc;

    @BeforeEach
    void inicializar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PermisoEstructuraRestController(permisoEstructuraService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private List<PermisoEstructuraDto> matriz(boolean editorEliminaPools) {
        return List.of(
                new PermisoEstructuraDto(RolUsuario.ADMINISTRADOR, false, true, true, true, true, true, true),
                new PermisoEstructuraDto(RolUsuario.EDITOR, true, true, true, editorEliminaPools, true, false, true),
                new PermisoEstructuraDto(RolUsuario.SOLO_LECTURA, false, false, false, false, false, false, false));
    }

    @Test
    void consultarDevuelveLaMatrizDePermisosPorRol() throws Exception {
        when(permisoEstructuraService.consultar(5L, USERNAME)).thenReturn(matriz(false));

        mockMvc.perform(get(RUTA).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rol").value("ADMINISTRADOR"))
                .andExpect(jsonPath("$[0].configurable").value(false))
                .andExpect(jsonPath("$[1].rol").value("EDITOR"))
                .andExpect(jsonPath("$[1].configurable").value(true))
                .andExpect(jsonPath("$[1].eliminarPool").value(false))
                .andExpect(jsonPath("$[2].crearLane").value(false));
    }

    @Test
    void configurarElEditorDevuelveLaMatrizActualizada() throws Exception {
        when(permisoEstructuraService.configurar(anyLong(), any(RolUsuario.class),
                any(ConfigurarPermisoEstructuraDto.class), anyString())).thenReturn(matriz(true));

        mockMvc.perform(put(RUTA + "/EDITOR").principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_PERMISOS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[1].eliminarPool").value(true))
                .andExpect(jsonPath("$[1].editarLane").value(false));

        ArgumentCaptor<ConfigurarPermisoEstructuraDto> capturado =
                ArgumentCaptor.forClass(ConfigurarPermisoEstructuraDto.class);
        verify(permisoEstructuraService).configurar(eq(5L), eq(RolUsuario.EDITOR), capturado.capture(),
                eq(USERNAME));
        assertThat(capturado.getValue().getEliminarPool()).isTrue();
        assertThat(capturado.getValue().getEditarLane()).isFalse();
    }

    @Test
    void configurarConUnPermisoSinIndicarDevuelve400() throws Exception {
        mockMvc.perform(put(RUTA + "/EDITOR").principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"crearPool":true,"editarPool":true}
                        """))
                .andExpect(status().isBadRequest());

        verify(permisoEstructuraService, never()).configurar(anyLong(), any(RolUsuario.class),
                any(ConfigurarPermisoEstructuraDto.class), anyString());
    }

    @Test
    void configurarUnRolInexistenteDevuelve400() throws Exception {
        mockMvc.perform(put(RUTA + "/AUDITOR").principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_PERMISOS))
                .andExpect(status().isBadRequest());

        verify(permisoEstructuraService, never()).configurar(anyLong(), any(RolUsuario.class),
                any(ConfigurarPermisoEstructuraDto.class), anyString());
    }

    @Test
    void quitarPermisosAlAdministradorDevuelve400() throws Exception {
        when(permisoEstructuraService.configurar(anyLong(), any(RolUsuario.class),
                any(ConfigurarPermisoEstructuraDto.class), anyString()))
                .thenThrow(new PermisoEstructuraNoValidoException(
                        "El administrador conserva siempre todos los permisos de estructura"));

        mockMvc.perform(put(RUTA + "/ADMINISTRADOR").principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_PERMISOS))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PERMISO_ESTRUCTURA_NO_VALIDO"));
    }

    @Test
    void configurarSinSerAdministradorDevuelve403() throws Exception {
        when(permisoEstructuraService.configurar(anyLong(), any(RolUsuario.class),
                any(ConfigurarPermisoEstructuraDto.class), anyString()))
                .thenThrow(new UsuarioSinPermisoException(
                        "Solo un administrador de la empresa propietaria puede configurar los permisos de estructura"));

        mockMvc.perform(put(RUTA + "/EDITOR").principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_PERMISOS))
                .andExpect(status().isForbidden());
    }
}

package co.edu.javeriana.procesosempresariales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.NombreProcesoDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.service.ProcesoService;

@ExtendWith(MockitoExtension.class)
class ProcesoRestControllerTest {

    private static final String USERNAME = "admin@alpes.com";
    private static final Principal PRINCIPAL = () -> USERNAME;

    private static final String JSON_CREACION = """
            {"nombre":"Ventas","descripcion":"Proceso comercial","categoria":"Comercial"}
            """;

    private static final String JSON_EDICION = """
            {"nombre":"Ventas Corporativas","descripcion":"Descripcion actualizada",
             "categoria":"Operaciones","estado":"PUBLICADO"}
            """;

    @Mock
    private ProcesoService procesoService;

    private MockMvc mockMvc;

    @BeforeEach
    void inicializar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProcesoRestController(procesoService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private ProcesoRespuestaDto procesoCreado() {
        ProcesoRespuestaDto proceso = new ProcesoRespuestaDto();
        proceso.setId(5L);
        proceso.setNombre("Ventas");
        proceso.setDescripcion("Proceso comercial");
        proceso.setCategoria("Comercial");
        proceso.setEstado(EstadoProceso.BORRADOR);
        proceso.setPoolId(80L);
        return proceso;
    }

    private ProcesoRespuestaDto procesoEditado() {
        ProcesoRespuestaDto proceso = procesoCreado();
        proceso.setNombre("Ventas Corporativas");
        proceso.setCategoria("Operaciones");
        proceso.setEstado(EstadoProceso.PUBLICADO);
        return proceso;
    }

    @Test
    void crearDevuelveDoscientosUnoConLaUbicacionDelNuevoProceso() throws Exception {
        when(procesoService.crear(any(CrearProcesoDto.class), anyString())).thenReturn(procesoCreado());

        mockMvc.perform(post("/api/procesos")
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_CREACION))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/procesos/5"))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.nombre").value("Ventas"))
                .andExpect(jsonPath("$.estado").value("BORRADOR"))
                .andExpect(jsonPath("$.poolId").value(80));
    }

    @Test
    void crearTomaLosDatosDelCuerpoYElUsuarioDeLaSesion() throws Exception {
        when(procesoService.crear(any(CrearProcesoDto.class), anyString())).thenReturn(procesoCreado());

        mockMvc.perform(post("/api/procesos")
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_CREACION))
                .andExpect(status().isCreated());

        ArgumentCaptor<CrearProcesoDto> enviado = ArgumentCaptor.forClass(CrearProcesoDto.class);
        verify(procesoService).crear(enviado.capture(), eq(USERNAME));
        assertThat(enviado.getValue().getNombre()).isEqualTo("Ventas");
        assertThat(enviado.getValue().getDescripcion()).isEqualTo("Proceso comercial");
        assertThat(enviado.getValue().getCategoria()).isEqualTo("Comercial");
    }

    @Test
    void crearConCuerpoInvalidoDevuelveCuatrocientos() throws Exception {
        mockMvc.perform(post("/api/procesos")
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"\",\"descripcion\":\"\",\"categoria\":\"\"}"))
                .andExpect(status().isBadRequest());

        verify(procesoService, never()).crear(any(CrearProcesoDto.class), anyString());
    }

    @Test
    void crearConNombreDuplicadoDevuelveCuatrocientosNueve() throws Exception {
        when(procesoService.crear(any(CrearProcesoDto.class), anyString()))
                .thenThrow(new NombreProcesoDuplicadoException("Ya existe un proceso con ese nombre en la empresa"));

        mockMvc.perform(post("/api/procesos")
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_CREACION))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("PROCESO_NOMBRE_DUPLICADO"));
    }

    @Test
    void editarDevuelveDoscientosConElProcesoActualizado() throws Exception {
        when(procesoService.editar(anyLong(), any(EditarProcesoDto.class), anyString()))
                .thenReturn(procesoEditado());

        mockMvc.perform(put("/api/procesos/5")
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_EDICION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ventas Corporativas"))
                .andExpect(jsonPath("$.categoria").value("Operaciones"))
                .andExpect(jsonPath("$.estado").value("PUBLICADO"));
    }

    @Test
    void editarDelegaEnElServicioConElIdentificadorDeLaRuta() throws Exception {
        when(procesoService.editar(anyLong(), any(EditarProcesoDto.class), anyString()))
                .thenReturn(procesoEditado());

        mockMvc.perform(put("/api/procesos/5")
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_EDICION))
                .andExpect(status().isOk());

        ArgumentCaptor<EditarProcesoDto> enviado = ArgumentCaptor.forClass(EditarProcesoDto.class);
        verify(procesoService).editar(eq(5L), enviado.capture(), eq(USERNAME));
        assertThat(enviado.getValue().getNombre()).isEqualTo("Ventas Corporativas");
        assertThat(enviado.getValue().getEstado()).isEqualTo(EstadoProceso.PUBLICADO);
    }

    @Test
    void editarUnProcesoInexistenteDevuelveCuatrocientosCuatro() throws Exception {
        when(procesoService.editar(anyLong(), any(EditarProcesoDto.class), anyString()))
                .thenThrow(new RecursoNoEncontradoException("El proceso no existe"));

        mockMvc.perform(put("/api/procesos/404")
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_EDICION))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void editarSinPermisoDevuelveCuatrocientosTres() throws Exception {
        when(procesoService.editar(anyLong(), any(EditarProcesoDto.class), anyString()))
                .thenThrow(new UsuarioSinPermisoException("Solo un administrador o editor puede modificar procesos"));

        mockMvc.perform(put("/api/procesos/5")
                .principal(PRINCIPAL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON_EDICION))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_SIN_PERMISO"));
    }

    @Test
    void eliminarDevuelveDoscientosCuatroSinCuerpo() throws Exception {
        ProcesoRespuestaDto eliminado = procesoCreado();
        eliminado.setEliminado(true);
        when(procesoService.eliminar(5L, USERNAME)).thenReturn(eliminado);

        mockMvc.perform(delete("/api/procesos/5").principal(PRINCIPAL))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(procesoService).eliminar(5L, USERNAME);
    }

    @Test
    void eliminarSinPermisoDevuelveCuatrocientosTres() throws Exception {
        when(procesoService.eliminar(anyLong(), anyString()))
                .thenThrow(new UsuarioSinPermisoException("Solo un administrador o editor puede crear o modificar procesos"));

        mockMvc.perform(delete("/api/procesos/5").principal(PRINCIPAL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_SIN_PERMISO"));
    }

    @Test
    void eliminarUnProcesoDeOtraEmpresaDevuelveCuatrocientosTres() throws Exception {
        when(procesoService.eliminar(anyLong(), anyString()))
                .thenThrow(new UsuarioSinPermisoException("El proceso no pertenece a la empresa del usuario"));

        mockMvc.perform(delete("/api/procesos/5").principal(PRINCIPAL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_SIN_PERMISO"));
    }

    @Test
    void eliminarUnProcesoYaEliminadoDevuelveCuatrocientosCuatro() throws Exception {
        when(procesoService.eliminar(anyLong(), anyString()))
                .thenThrow(new RecursoNoEncontradoException("El proceso ya fue eliminado"));

        mockMvc.perform(delete("/api/procesos/5").principal(PRINCIPAL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }
}

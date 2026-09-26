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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.dto.AlcanceProceso;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.FiltroProcesosDto;
import co.edu.javeriana.procesosempresariales.dto.HistorialProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoResumenDto;
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
    private ProcesoResumenDto resumenCompartido() {
        ProcesoResumenDto resumen = new ProcesoResumenDto();
        resumen.setId(6L);
        resumen.setNombre("Compras");
        resumen.setCategoria("Abastecimiento");
        resumen.setEstado(EstadoProceso.PUBLICADO);
        resumen.setEmpresaPropietariaId(99L);
        resumen.setEmpresaPropietariaNombre("Andes Distribucion");
        resumen.setSoloLectura(true);
        return resumen;
    }

    @Test
    void consultarDevuelveUnaPaginaDeResumenesConLaEmpresaPropietaria() throws Exception {
        when(procesoService.consultarProcesos(any(FiltroProcesosDto.class), eq(USERNAME)))
                .thenReturn(new PageImpl<>(List.of(resumenCompartido()), PageRequest.of(1, 10), 11));

        mockMvc.perform(get("/api/procesos").principal(PRINCIPAL).param("alcance", "COMPARTIDOS")
                .param("q", "comp").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(6))
                .andExpect(jsonPath("$.content[0].empresaPropietariaId").value(99))
                .andExpect(jsonPath("$.content[0].empresaPropietariaNombre").value("Andes Distribucion"))
                .andExpect(jsonPath("$.content[0].soloLectura").value(true))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(11));

        ArgumentCaptor<FiltroProcesosDto> capturado = ArgumentCaptor.forClass(FiltroProcesosDto.class);
        verify(procesoService).consultarProcesos(capturado.capture(), eq(USERNAME));
        assertThat(capturado.getValue().getAlcance()).isEqualTo(AlcanceProceso.COMPARTIDOS);
        assertThat(capturado.getValue().getQ()).isEqualTo("comp");
        assertThat(capturado.getValue().getPage()).isEqualTo(1);
    }

    @Test
    void consultarSinParametrosDelegaUnFiltroVacio() throws Exception {
        when(procesoService.consultarProcesos(any(FiltroProcesosDto.class), eq(USERNAME)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        mockMvc.perform(get("/api/procesos").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());

        ArgumentCaptor<FiltroProcesosDto> capturado = ArgumentCaptor.forClass(FiltroProcesosDto.class);
        verify(procesoService).consultarProcesos(capturado.capture(), eq(USERNAME));
        assertThat(capturado.getValue().getAlcance()).isNull();
    }

    @Test
    void obtenerDevuelveElProcesoVisibleIndicandoSiEsDeSoloLectura() throws Exception {
        ProcesoRespuestaDto compartido = procesoCreado();
        compartido.setEmpresaPropietariaId(99L);
        compartido.setEmpresaPropietariaNombre("Andes Distribucion");
        compartido.setSoloLectura(true);
        when(procesoService.obtenerVisible(5L, USERNAME)).thenReturn(compartido);

        mockMvc.perform(get("/api/procesos/5").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.poolId").value(80))
                .andExpect(jsonPath("$.empresaPropietariaNombre").value("Andes Distribucion"))
                .andExpect(jsonPath("$.soloLectura").value(true));
    }

    @Test
    void obtenerUnProcesoAjenoNoCompartidoDevuelve403() throws Exception {
        when(procesoService.obtenerVisible(5L, USERNAME))
                .thenThrow(new UsuarioSinPermisoException("El proceso no pertenece a la empresa del usuario"));

        mockMvc.perform(get("/api/procesos/5").principal(PRINCIPAL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_SIN_PERMISO"));
    }

    @Test
    void obtenerUnProcesoInexistenteDevuelve404() throws Exception {
        when(procesoService.obtenerVisible(404L, USERNAME))
                .thenThrow(new RecursoNoEncontradoException("El proceso no existe"));

        mockMvc.perform(get("/api/procesos/404").principal(PRINCIPAL))
                .andExpect(status().isNotFound());
    }

    @Test
    void historialDevuelveLosCambiosDelProcesoPropio() throws Exception {
        HistorialProcesoRespuestaDto cambio = new HistorialProcesoRespuestaDto();
        cambio.setUsuarioCorreo(USERNAME);
        cambio.setCambiosRealizados("pool creado: 'Cliente' (EXTERNO)");
        when(procesoService.consultarHistorial(5L, USERNAME)).thenReturn(List.of(cambio));

        mockMvc.perform(get("/api/procesos/5/historial").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].usuarioCorreo").value(USERNAME))
                .andExpect(jsonPath("$[0].cambiosRealizados").value("pool creado: 'Cliente' (EXTERNO)"));
    }

    @Test
    void elHistorialDeUnProcesoCompartidoNoSeExponeALaEmpresaInvitada() throws Exception {
        when(procesoService.consultarHistorial(5L, USERNAME))
                .thenThrow(new UsuarioSinPermisoException("El proceso no pertenece a la empresa del usuario"));

        mockMvc.perform(get("/api/procesos/5/historial").principal(PRINCIPAL))
                .andExpect(status().isForbidden());
    }
}

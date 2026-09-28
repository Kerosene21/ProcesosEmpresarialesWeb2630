package co.edu.javeriana.procesosempresariales.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import java.time.LocalDateTime;
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

import co.edu.javeriana.procesosempresariales.domain.AccionRolProceso;
import co.edu.javeriana.procesosempresariales.dto.CrearRolProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarRolProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.FiltroRolesProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.HistorialRolProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoUsoRolDto;
import co.edu.javeriana.procesosempresariales.dto.RolProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.RolProcesoResumenDto;
import co.edu.javeriana.procesosempresariales.dto.VisibilidadRolProceso;
import co.edu.javeriana.procesosempresariales.exception.NombreRolProcesoDuplicadoException;
import co.edu.javeriana.procesosempresariales.exception.RecursoNoEncontradoException;
import co.edu.javeriana.procesosempresariales.exception.RolProcesoEnUsoException;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;
import co.edu.javeriana.procesosempresariales.service.RolProcesoService;

@ExtendWith(MockitoExtension.class)
class RolProcesoRestControllerTest {

    private static final String USERNAME = "admin@alpes.com";
    private static final Principal PRINCIPAL = () -> USERNAME;
    private static final String RUTA_ROLES = "/api/roles-proceso";
    private static final String RUTA_ROL = "/api/roles-proceso/40";
    private static final String ANALISTA = "Analista de credito";

    private static final String JSON_ROL = """
            {"nombre":"Analista de credito","descripcion":"Evalua el riesgo"}
            """;

    private static final String JSON_SIN_NOMBRE = """
            {"nombre":"   ","descripcion":"Evalua el riesgo"}
            """;

    private static final String JSON_SIN_DESCRIPCION = """
            {"nombre":"Analista de credito"}
            """;

    @Mock
    private RolProcesoService rolProcesoService;

    private MockMvc mockMvc;

    @BeforeEach
    void inicializar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RolProcesoRestController(rolProcesoService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    private RolProcesoRespuestaDto respuesta(boolean activo) {
        RolProcesoRespuestaDto rol = new RolProcesoRespuestaDto();
        rol.setId(40L);
        rol.setNombre(ANALISTA);
        rol.setDescripcion("Evalua el riesgo");
        rol.setActivo(activo);
        return rol;
    }

    private RolProcesoResumenDto resumen(boolean enUso) {
        RolProcesoResumenDto rol = new RolProcesoResumenDto();
        rol.setId(40L);
        rol.setNombre(ANALISTA);
        rol.setDescripcion("Evalua el riesgo");
        rol.setActivo(true);
        rol.setEnUso(enUso);
        rol.setPuedeEliminar(!enUso);
        if (enUso) {
            rol.setProcesos(List.of(new ProcesoUsoRolDto(5L, "Ventas", 1, 3)));
        }
        return rol;
    }

    @Test
    void crearDevuelve201ConLaUbicacionDelRol() throws Exception {
        when(rolProcesoService.crear(any(CrearRolProcesoDto.class), eq(USERNAME))).thenReturn(respuesta(true));

        mockMvc.perform(post(RUTA_ROLES).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_ROL))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/roles-proceso/40"))
                .andExpect(jsonPath("$.id").value(40))
                .andExpect(jsonPath("$.nombre").value(ANALISTA))
                .andExpect(jsonPath("$.activo").value(true));

        ArgumentCaptor<CrearRolProcesoDto> capturado = ArgumentCaptor.forClass(CrearRolProcesoDto.class);
        verify(rolProcesoService).crear(capturado.capture(), eq(USERNAME));
        assertThat(capturado.getValue().getNombre()).isEqualTo(ANALISTA);
        assertThat(capturado.getValue().getDescripcion()).isEqualTo("Evalua el riesgo");
    }

    @Test
    void crearDevuelve400SinNombreYNoLlamaAlServicio() throws Exception {
        mockMvc.perform(post(RUTA_ROLES).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_SIN_NOMBRE))
                .andExpect(status().isBadRequest());

        verify(rolProcesoService, never()).crear(any(CrearRolProcesoDto.class), anyString());
    }

    @Test
    void crearDevuelve400SinDescripcion() throws Exception {
        mockMvc.perform(post(RUTA_ROLES).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_SIN_DESCRIPCION))
                .andExpect(status().isBadRequest());

        verify(rolProcesoService, never()).crear(any(CrearRolProcesoDto.class), anyString());
    }

    @Test
    void crearDevuelve403CuandoElUsuarioNoEsAdministrador() throws Exception {
        when(rolProcesoService.crear(any(CrearRolProcesoDto.class), eq(USERNAME)))
                .thenThrow(new UsuarioSinPermisoException("Solo un administrador puede crear roles de proceso"));

        mockMvc.perform(post(RUTA_ROLES).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_ROL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_SIN_PERMISO"));
    }

    @Test
    void crearDevuelve409CuandoElNombreYaExisteEnLaEmpresa() throws Exception {
        when(rolProcesoService.crear(any(CrearRolProcesoDto.class), eq(USERNAME)))
                .thenThrow(new NombreRolProcesoDuplicadoException("Ya existe un rol de proceso con ese nombre"));

        mockMvc.perform(post(RUTA_ROLES).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_ROL))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ROL_PROCESO_NOMBRE_DUPLICADO"));
    }

    @Test
    void consultarDevuelveLaPaginaDeRolesConSuUso() throws Exception {
        when(rolProcesoService.consultar(any(FiltroRolesProcesoDto.class), eq(USERNAME)))
                .thenReturn(new PageImpl<>(List.of(resumen(true)), PageRequest.of(0, 10), 1));

        mockMvc.perform(get(RUTA_ROLES).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(40))
                .andExpect(jsonPath("$.content[0].nombre").value(ANALISTA))
                .andExpect(jsonPath("$.content[0].descripcion").value("Evalua el riesgo"))
                .andExpect(jsonPath("$.content[0].activo").value(true))
                .andExpect(jsonPath("$.content[0].enUso").value(true))
                .andExpect(jsonPath("$.content[0].puedeEliminar").value(false))
                .andExpect(jsonPath("$.content[0].procesos[0].nombre").value("Ventas"))
                .andExpect(jsonPath("$.content[0].procesos[0].actividadesActivas").value(3))
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void consultarEnviaLaBusquedaLaVisibilidadYLaPagina() throws Exception {
        when(rolProcesoService.consultar(any(FiltroRolesProcesoDto.class), eq(USERNAME)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 10), 10));

        mockMvc.perform(get(RUTA_ROLES).principal(PRINCIPAL)
                .param("q", "analista")
                .param("visibilidad", "INACTIVOS")
                .param("page", "1"))
                .andExpect(status().isOk());

        ArgumentCaptor<FiltroRolesProcesoDto> capturado = ArgumentCaptor.forClass(FiltroRolesProcesoDto.class);
        verify(rolProcesoService).consultar(capturado.capture(), eq(USERNAME));
        assertThat(capturado.getValue().getQ()).isEqualTo("analista");
        assertThat(capturado.getValue().getVisibilidad()).isEqualTo(VisibilidadRolProceso.INACTIVOS);
        assertThat(capturado.getValue().getPage()).isEqualTo(1);
    }

    @Test
    void obtenerDevuelveElDetalleDelRol() throws Exception {
        when(rolProcesoService.obtener(40L, USERNAME)).thenReturn(resumen(false));

        mockMvc.perform(get(RUTA_ROL).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(40))
                .andExpect(jsonPath("$.enUso").value(false))
                .andExpect(jsonPath("$.puedeEliminar").value(true));
    }

    @Test
    void obtenerDevuelve403ParaUnRolDeOtraEmpresa() throws Exception {
        when(rolProcesoService.obtener(40L, USERNAME))
                .thenThrow(new UsuarioSinPermisoException("El rol de proceso no pertenece a la empresa del usuario"));

        mockMvc.perform(get(RUTA_ROL).principal(PRINCIPAL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("USUARIO_SIN_PERMISO"));
    }

    @Test
    void editarDevuelve200ConElRolActualizado() throws Exception {
        RolProcesoRespuestaDto editado = respuesta(true);
        editado.setNombre("Analista senior");
        when(rolProcesoService.editar(eq(40L), any(EditarRolProcesoDto.class), eq(USERNAME))).thenReturn(editado);

        mockMvc.perform(put(RUTA_ROL).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"nombre":"Analista senior","descripcion":"Evalua el riesgo"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(40))
                .andExpect(jsonPath("$.nombre").value("Analista senior"));
    }

    @Test
    void editarDevuelve400ConDatosInvalidos() throws Exception {
        mockMvc.perform(put(RUTA_ROL).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_SIN_NOMBRE))
                .andExpect(status().isBadRequest());

        verify(rolProcesoService, never()).editar(any(), any(EditarRolProcesoDto.class), anyString());
    }

    @Test
    void editarDevuelve409CuandoElNuevoNombreYaExiste() throws Exception {
        when(rolProcesoService.editar(eq(40L), any(EditarRolProcesoDto.class), eq(USERNAME)))
                .thenThrow(new NombreRolProcesoDuplicadoException("Ya existe un rol de proceso con ese nombre"));

        mockMvc.perform(put(RUTA_ROL).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_ROL))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ROL_PROCESO_NOMBRE_DUPLICADO"));
    }

    @Test
    void editarDevuelve404CuandoElRolFueEliminado() throws Exception {
        when(rolProcesoService.editar(eq(40L), any(EditarRolProcesoDto.class), eq(USERNAME)))
                .thenThrow(new RecursoNoEncontradoException("El rol de proceso ya fue eliminado"));

        mockMvc.perform(put(RUTA_ROL).principal(PRINCIPAL).contentType(MediaType.APPLICATION_JSON)
                .content(JSON_ROL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void laConsultaPreviaDeEliminacionDevuelveElImpacto() throws Exception {
        when(rolProcesoService.obtenerParaEliminar(40L, USERNAME)).thenReturn(resumen(true));

        mockMvc.perform(get(RUTA_ROL + "/eliminacion").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enUso").value(true))
                .andExpect(jsonPath("$.puedeEliminar").value(false))
                .andExpect(jsonPath("$.procesos[0].nombre").value("Ventas"));
    }

    @Test
    void eliminarDevuelve204() throws Exception {
        when(rolProcesoService.eliminar(40L, USERNAME)).thenReturn(respuesta(false));

        mockMvc.perform(delete(RUTA_ROL).principal(PRINCIPAL)).andExpect(status().isNoContent());

        verify(rolProcesoService).eliminar(40L, USERNAME);
    }

    @Test
    void eliminarUnRolEnUsoDevuelve409ConLosProcesosDondeSeUsa() throws Exception {
        when(rolProcesoService.eliminar(40L, USERNAME)).thenThrow(new RolProcesoEnUsoException(
                "El rol de proceso 'Analista de credito' no se puede eliminar porque lo usan lanes de los procesos:"
                        + " Ventas, Compras",
                List.of("Ventas", "Compras")));

        mockMvc.perform(delete(RUTA_ROL).principal(PRINCIPAL))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ROL_PROCESO_EN_USO"))
                .andExpect(jsonPath("$.procesos[0]").value("Ventas"))
                .andExpect(jsonPath("$.procesos[1]").value("Compras"));
    }

    @Test
    void eliminarDosVecesDevuelve404() throws Exception {
        when(rolProcesoService.eliminar(40L, USERNAME))
                .thenThrow(new RecursoNoEncontradoException("El rol de proceso ya fue eliminado"));

        mockMvc.perform(delete(RUTA_ROL).principal(PRINCIPAL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("RECURSO_NO_ENCONTRADO"));
    }

    @Test
    void elHistorialDelRolSeDevuelveEnOrden() throws Exception {
        HistorialRolProcesoRespuestaDto entrada = new HistorialRolProcesoRespuestaDto();
        entrada.setFecha(LocalDateTime.of(2026, 9, 1, 10, 30));
        entrada.setUsuarioCorreo(USERNAME);
        entrada.setAccion(AccionRolProceso.EDICION);
        entrada.setCambiosRealizados("nombre: 'Analista' -> 'Analista senior'");
        when(rolProcesoService.consultarHistorial(40L, USERNAME)).thenReturn(List.of(entrada));

        mockMvc.perform(get(RUTA_ROL + "/historial").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].usuarioCorreo").value(USERNAME))
                .andExpect(jsonPath("$[0].accion").value("EDICION"))
                .andExpect(jsonPath("$[0].cambiosRealizados").value("nombre: 'Analista' -> 'Analista senior'"));
    }
}

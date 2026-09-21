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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.FiltroProcesosDto;
import co.edu.javeriana.procesosempresariales.dto.HistorialProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ProcesoResumenDto;
import co.edu.javeriana.procesosempresariales.dto.VisibilidadProceso;
import co.edu.javeriana.procesosempresariales.dto.ProcesoRespuestaDto;
import co.edu.javeriana.procesosempresariales.service.ProcesoService;

@ExtendWith(MockitoExtension.class)
class ProcesoControllerTest {

    private static final String USERNAME = "admin@alpes.com";
    private static final Principal PRINCIPAL = () -> USERNAME;

    @Mock
    private ProcesoService procesoService;

    private MockMvc mockMvc;

    @BeforeEach
    void inicializar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProcesoController(procesoService)).build();
    }

    private ProcesoRespuestaDto procesoExistente() {
        ProcesoRespuestaDto proceso = new ProcesoRespuestaDto();
        proceso.setId(5L);
        proceso.setNombre("Ventas");
        proceso.setDescripcion("Proceso comercial");
        proceso.setCategoria("Comercial");
        proceso.setEstado(EstadoProceso.BORRADOR);
        proceso.setPoolId(80L);
        return proceso;
    }

    @Test
    void elFormularioDeCreacionSeAbreConUnDtoVacio() throws Exception {
        MvcResult resultado = mockMvc.perform(get("/procesos/nuevo"))
                .andExpect(status().isOk())
                .andExpect(view().name("procesos/formularioprocesos"))
                .andReturn();

        CrearProcesoDto proceso = (CrearProcesoDto) resultado.getModelAndView().getModel().get("proceso");
        assertThat(proceso).isNotNull();
        assertThat(proceso.getNombre()).isNull();
    }

    @Test
    void verMuestraElProcesoYSiElUsuarioPuedeEditarlo() throws Exception {
        when(procesoService.obtener(5L, USERNAME)).thenReturn(procesoExistente());
        when(procesoService.puedeEditar(USERNAME)).thenReturn(true);

        mockMvc.perform(get("/procesos/5").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(view().name("procesos/proceso"))
                .andExpect(model().attribute("puedeEditar", true))
                .andExpect(model().attributeExists("proceso"));
    }

    @Test
    void verMarcaComoNoEditableAlUsuarioDeSoloLectura() throws Exception {
        when(procesoService.obtener(5L, USERNAME)).thenReturn(procesoExistente());
        when(procesoService.puedeEditar(USERNAME)).thenReturn(false);

        mockMvc.perform(get("/procesos/5").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(view().name("procesos/proceso"))
                .andExpect(model().attribute("puedeEditar", false));
    }

    @Test
    void elFormularioDeEdicionSePrecargaConLosDatosActualesDelProceso() throws Exception {
        when(procesoService.obtener(5L, USERNAME)).thenReturn(procesoExistente());

        MvcResult resultado = mockMvc.perform(get("/procesos/5/editar").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(view().name("procesos/formularioprocesoseditar"))
                .andExpect(model().attribute("procesoId", 5L))
                .andExpect(model().attribute("estados", EstadoProceso.values()))
                .andReturn();

        EditarProcesoDto proceso = (EditarProcesoDto) resultado.getModelAndView().getModel().get("proceso");
        assertThat(proceso.getNombre()).isEqualTo("Ventas");
        assertThat(proceso.getDescripcion()).isEqualTo("Proceso comercial");
        assertThat(proceso.getCategoria()).isEqualTo("Comercial");
        assertThat(proceso.getEstado()).isEqualTo(EstadoProceso.BORRADOR);
    }

    @Test
    void actualizarVuelveAlFormularioCuandoLaValidacionFalla() throws Exception {
        mockMvc.perform(post("/procesos/5")
                .principal(PRINCIPAL)
                .param("nombre", "")
                .param("descripcion", "Descripcion actualizada")
                .param("categoria", "Operaciones"))
                .andExpect(status().isOk())
                .andExpect(view().name("procesos/formularioprocesoseditar"))
                .andExpect(model().attribute("procesoId", 5L))
                .andExpect(model().attributeExists("estados"))
                .andExpect(model().attributeHasFieldErrors("proceso", "nombre", "estado"));

        verify(procesoService, never()).editar(anyLong(), any(EditarProcesoDto.class), anyString());
    }

    @Test
    void actualizarDelegaEnElServicioYRedirigeAlDetalleDelProceso() throws Exception {
        when(procesoService.editar(anyLong(), any(EditarProcesoDto.class), anyString()))
                .thenReturn(procesoExistente());

        mockMvc.perform(post("/procesos/5")
                .principal(PRINCIPAL)
                .param("nombre", "Ventas Corporativas")
                .param("descripcion", "Descripcion actualizada")
                .param("categoria", "Operaciones")
                .param("estado", "PUBLICADO"))
                .andExpect(redirectedUrl("/procesos/5"))
                .andExpect(flash().attribute("mensaje", "Proceso actualizado correctamente"));

        ArgumentCaptor<EditarProcesoDto> enviado = ArgumentCaptor.forClass(EditarProcesoDto.class);
        verify(procesoService).editar(eq(5L), enviado.capture(), eq(USERNAME));
        assertThat(enviado.getValue().getNombre()).isEqualTo("Ventas Corporativas");
        assertThat(enviado.getValue().getDescripcion()).isEqualTo("Descripcion actualizada");
        assertThat(enviado.getValue().getCategoria()).isEqualTo("Operaciones");
        assertThat(enviado.getValue().getEstado()).isEqualTo(EstadoProceso.PUBLICADO);
    }

    @Test
    void crearVuelveAlFormularioCuandoLaValidacionFalla() throws Exception {
        mockMvc.perform(post("/procesos")
                .principal(PRINCIPAL)
                .param("nombre", "")
                .param("descripcion", "")
                .param("categoria", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("procesos/formularioprocesos"))
                .andExpect(model().attributeHasFieldErrors("proceso", "nombre", "descripcion", "categoria"));

        verify(procesoService, never()).crear(any(CrearProcesoDto.class), anyString());
    }

    @Test
    void crearDelegaEnElServicioYRedirigeAlDetalleDelProcesoCreado() throws Exception {
        when(procesoService.crear(any(CrearProcesoDto.class), anyString())).thenReturn(procesoExistente());

        mockMvc.perform(post("/procesos")
                .principal(PRINCIPAL)
                .param("nombre", "Ventas")
                .param("descripcion", "Proceso comercial")
                .param("categoria", "Comercial"))
                .andExpect(redirectedUrl("/procesos/5"))
                .andExpect(flash().attribute("mensaje", "Proceso creado en estado borrador"));

        ArgumentCaptor<CrearProcesoDto> enviado = ArgumentCaptor.forClass(CrearProcesoDto.class);
        verify(procesoService).crear(enviado.capture(), eq(USERNAME));
        assertThat(enviado.getValue().getNombre()).isEqualTo("Ventas");
        assertThat(enviado.getValue().getDescripcion()).isEqualTo("Proceso comercial");
        assertThat(enviado.getValue().getCategoria()).isEqualTo("Comercial");
    }

    @Test
    void elHistorialMuestraLasEntradasDelProcesoConSuUsuarioYSuFecha() throws Exception {
        when(procesoService.obtener(5L, USERNAME)).thenReturn(procesoExistente());
        when(procesoService.consultarHistorial(5L, USERNAME)).thenReturn(List.of(
                entradaHistorial("estado: 'BORRADOR' -> 'PUBLICADO'", "BORRADOR"),
                entradaHistorial("nombre: 'Ventas' -> 'Ventas Corporativas'", "BORRADOR")));

        MvcResult resultado = mockMvc.perform(get("/procesos/5/historial").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(view().name("procesos/historial"))
                .andExpect(model().attributeExists("proceso"))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<HistorialProcesoRespuestaDto> historial =
                (List<HistorialProcesoRespuestaDto>) resultado.getModelAndView().getModel().get("historial");
        assertThat(historial).hasSize(2);
        assertThat(historial.get(0).getCambiosRealizados()).isEqualTo("estado: 'BORRADOR' -> 'PUBLICADO'");
        assertThat(historial.get(0).getUsuarioCorreo()).isEqualTo(USERNAME);
        assertThat(historial.get(0).getFecha()).isNotNull();
    }

    @Test
    void elHistorialVacioSeMuestraSinEntradas() throws Exception {
        when(procesoService.obtener(5L, USERNAME)).thenReturn(procesoExistente());
        when(procesoService.consultarHistorial(5L, USERNAME)).thenReturn(List.of());

        MvcResult resultado = mockMvc.perform(get("/procesos/5/historial").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(view().name("procesos/historial"))
                .andReturn();

        @SuppressWarnings("unchecked")
        List<HistorialProcesoRespuestaDto> historial =
                (List<HistorialProcesoRespuestaDto>) resultado.getModelAndView().getModel().get("historial");
        assertThat(historial).isEmpty();
    }

    @Test
    void laConfirmacionDeEliminacionMuestraElProcesoYNoEliminaNada() throws Exception {
        when(procesoService.obtenerParaEliminar(5L, USERNAME)).thenReturn(procesoExistente());

        MvcResult resultado = mockMvc.perform(get("/procesos/5/eliminar").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(view().name("procesos/confirmareliminacion"))
                .andReturn();

        ProcesoRespuestaDto proceso = (ProcesoRespuestaDto) resultado.getModelAndView().getModel().get("proceso");
        assertThat(proceso.getNombre()).isEqualTo("Ventas");
        assertThat(proceso.isEliminado()).isFalse();
        verify(procesoService, never()).eliminar(anyLong(), anyString());
    }

    @Test
    void elDetalleIndicaSiElUsuarioPuedeEliminarElProceso() throws Exception {
        when(procesoService.obtener(5L, USERNAME)).thenReturn(procesoExistente());
        when(procesoService.puedeEditar(USERNAME)).thenReturn(true);
        when(procesoService.puedeEliminar(USERNAME)).thenReturn(true);

        mockMvc.perform(get("/procesos/5").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(model().attribute("puedeEliminar", true));
    }

    @Test
    void elDetalleMarcaComoNoEliminableAQuienNoEsAdministrador() throws Exception {
        when(procesoService.obtener(5L, USERNAME)).thenReturn(procesoExistente());
        when(procesoService.puedeEditar(USERNAME)).thenReturn(true);
        when(procesoService.puedeEliminar(USERNAME)).thenReturn(false);

        mockMvc.perform(get("/procesos/5").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(model().attribute("puedeEditar", true))
                .andExpect(model().attribute("puedeEliminar", false));
    }

    @Test
    void eliminarDelegaEnElServicioYRedirigeAlDetalleDelProceso() throws Exception {
        ProcesoRespuestaDto eliminado = procesoExistente();
        eliminado.setEliminado(true);
        when(procesoService.eliminar(5L, USERNAME)).thenReturn(eliminado);

        mockMvc.perform(post("/procesos/5/eliminar").principal(PRINCIPAL))
                .andExpect(redirectedUrl("/procesos/5"))
                .andExpect(flash().attributeExists("mensaje"));

        verify(procesoService).eliminar(5L, USERNAME);
    }

    @Test
    void elDetalleIndicaCuandoElProcesoEstaEliminado() throws Exception {
        ProcesoRespuestaDto eliminado = procesoExistente();
        eliminado.setEliminado(true);
        when(procesoService.obtener(5L, USERNAME)).thenReturn(eliminado);
        when(procesoService.puedeEditar(USERNAME)).thenReturn(true);

        MvcResult resultado = mockMvc.perform(get("/procesos/5").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(view().name("procesos/proceso"))
                .andReturn();

        ProcesoRespuestaDto proceso = (ProcesoRespuestaDto) resultado.getModelAndView().getModel().get("proceso");
        assertThat(proceso.isEliminado()).isTrue();
    }

    private ProcesoResumenDto resumen(Long id, String nombre, boolean eliminado) {
        ProcesoResumenDto resumen = new ProcesoResumenDto();
        resumen.setId(id);
        resumen.setNombre(nombre);
        resumen.setDescripcion("Proceso comercial");
        resumen.setCategoria("Comercial");
        resumen.setEstado(EstadoProceso.BORRADOR);
        resumen.setEliminado(eliminado);
        return resumen;
    }

    private void devolverListado(ProcesoResumenDto... procesos) {
        when(procesoService.consultarProcesos(any(FiltroProcesosDto.class), anyString()))
                .thenReturn(new PageImpl<>(List.of(procesos), PageRequest.of(0, 10), procesos.length));
        when(procesoService.categoriasDisponibles(USERNAME)).thenReturn(List.of("Comercial", "Operaciones"));
    }

    @Test
    void elListadoMuestraLosProcesosDeLaEmpresaConSusCategoriasYEstados() throws Exception {
        devolverListado(resumen(5L, "Ventas", false), resumen(6L, "Compras", false));

        MvcResult resultado = mockMvc.perform(get("/procesos").principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(view().name("procesos/lista"))
                .andExpect(model().attributeExists("procesos", "filtro"))
                .andExpect(model().attribute("categorias", List.of("Comercial", "Operaciones")))
                .andExpect(model().attribute("estados", EstadoProceso.values()))
                .andExpect(model().attribute("visibilidades", VisibilidadProceso.values()))
                .andReturn();

        @SuppressWarnings("unchecked")
        Page<ProcesoResumenDto> procesos =
                (Page<ProcesoResumenDto>) resultado.getModelAndView().getModel().get("procesos");
        assertThat(procesos.getContent()).extracting(ProcesoResumenDto::getNombre)
                .containsExactly("Ventas", "Compras");
    }

    @Test
    void elListadoTrasladaLaBusquedaYLosFiltrosAlServicio() throws Exception {
        devolverListado(resumen(5L, "Ventas", false));

        mockMvc.perform(get("/procesos").principal(PRINCIPAL)
                .param("q", "venta")
                .param("estado", "PUBLICADO")
                .param("categoria", "Comercial")
                .param("visibilidad", "INACTIVOS")
                .param("page", "2"))
                .andExpect(status().isOk());

        ArgumentCaptor<FiltroProcesosDto> enviado = ArgumentCaptor.forClass(FiltroProcesosDto.class);
        verify(procesoService).consultarProcesos(enviado.capture(), eq(USERNAME));
        assertThat(enviado.getValue().getQ()).isEqualTo("venta");
        assertThat(enviado.getValue().getEstado()).isEqualTo(EstadoProceso.PUBLICADO);
        assertThat(enviado.getValue().getCategoria()).isEqualTo("Comercial");
        assertThat(enviado.getValue().getVisibilidad()).isEqualTo(VisibilidadProceso.INACTIVOS);
        assertThat(enviado.getValue().getPage()).isEqualTo(2);
    }

    @Test
    void elListadoSinParametrosNoImponeNingunFiltroYDejaElDefectoAlServicio() throws Exception {
        devolverListado();

        mockMvc.perform(get("/procesos").principal(PRINCIPAL)).andExpect(status().isOk());

        ArgumentCaptor<FiltroProcesosDto> enviado = ArgumentCaptor.forClass(FiltroProcesosDto.class);
        verify(procesoService).consultarProcesos(enviado.capture(), eq(USERNAME));
        assertThat(enviado.getValue().getQ()).isNull();
        assertThat(enviado.getValue().getEstado()).isNull();
        assertThat(enviado.getValue().getCategoria()).isNull();
    }

    @Test
    void elListadoAceptaParametrosVaciosSinFallar() throws Exception {
        devolverListado();

        mockMvc.perform(get("/procesos").principal(PRINCIPAL)
                .param("q", "")
                .param("estado", "")
                .param("categoria", "")
                .param("visibilidad", "")
                .param("page", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("procesos/lista"));
    }

    @Test
    void elListadoDevuelveElFiltroAlModeloParaQueLaVistaLoConserve() throws Exception {
        devolverListado(resumen(5L, "Ventas", true));

        MvcResult resultado = mockMvc.perform(get("/procesos").principal(PRINCIPAL)
                .param("q", "venta")
                .param("visibilidad", "INACTIVOS"))
                .andExpect(status().isOk())
                .andReturn();

        FiltroProcesosDto filtro = (FiltroProcesosDto) resultado.getModelAndView().getModel().get("filtro");
        assertThat(filtro.getQ()).isEqualTo("venta");
        assertThat(filtro.getVisibilidad()).isEqualTo(VisibilidadProceso.INACTIVOS);
    }

    private HistorialProcesoRespuestaDto entradaHistorial(String cambios, String estadoAnterior) {
        HistorialProcesoRespuestaDto entrada = new HistorialProcesoRespuestaDto();
        entrada.setFecha(LocalDateTime.now());
        entrada.setUsuarioCorreo(USERNAME);
        entrada.setEstadoAnterior(estadoAnterior);
        entrada.setCambiosRealizados(cambios);
        return entrada;
    }
}

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import co.edu.javeriana.procesosempresariales.dto.CrearProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarProcesoDto;
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
    void verRedirigeAlLoginCuandoNoHayUsuarioAutenticado() throws Exception {
        mockMvc.perform(get("/procesos/5"))
                .andExpect(redirectedUrl("/login"));

        verify(procesoService, never()).obtener(anyLong(), anyString());
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
    void elFormularioDeEdicionRedirigeAlLoginCuandoNoHayUsuarioAutenticado() throws Exception {
        mockMvc.perform(get("/procesos/5/editar"))
                .andExpect(redirectedUrl("/login"));

        verify(procesoService, never()).obtener(anyLong(), anyString());
    }

    @Test
    void elFormularioDeEdicionDevuelveAlDetalleCuandoElUsuarioNoPuedeEditar() throws Exception {
        when(procesoService.obtener(5L, USERNAME)).thenReturn(procesoExistente());
        when(procesoService.puedeEditar(USERNAME)).thenReturn(false);

        mockMvc.perform(get("/procesos/5/editar").principal(PRINCIPAL))
                .andExpect(redirectedUrl("/procesos/5"));
    }

    @Test
    void elFormularioDeEdicionSePrecargaConLosDatosActualesDelProceso() throws Exception {
        when(procesoService.obtener(5L, USERNAME)).thenReturn(procesoExistente());
        when(procesoService.puedeEditar(USERNAME)).thenReturn(true);

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
    void actualizarRedirigeAlLoginCuandoNoHayUsuarioAutenticado() throws Exception {
        mockMvc.perform(post("/procesos/5")
                .param("nombre", "Ventas Corporativas")
                .param("descripcion", "Descripcion actualizada")
                .param("categoria", "Operaciones")
                .param("estado", "PUBLICADO"))
                .andExpect(redirectedUrl("/login"));

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
    void crearRedirigeAlLoginCuandoNoHayUsuarioAutenticado() throws Exception {
        mockMvc.perform(post("/procesos")
                .param("nombre", "Ventas")
                .param("descripcion", "Proceso comercial")
                .param("categoria", "Comercial"))
                .andExpect(redirectedUrl("/login"));

        verify(procesoService, never()).crear(any(CrearProcesoDto.class), anyString());
    }

    @Test
    void crearDelegaEnElServicioYRedirigeAlFormularioConElMensajeDeConfirmacion() throws Exception {
        when(procesoService.crear(any(CrearProcesoDto.class), anyString())).thenReturn(procesoExistente());

        mockMvc.perform(post("/procesos")
                .principal(PRINCIPAL)
                .param("nombre", "Ventas")
                .param("descripcion", "Proceso comercial")
                .param("categoria", "Comercial"))
                .andExpect(redirectedUrl("/procesos/nuevo"))
                .andExpect(flash().attribute("mensaje", "Proceso creado en estado borrador"));

        ArgumentCaptor<CrearProcesoDto> enviado = ArgumentCaptor.forClass(CrearProcesoDto.class);
        verify(procesoService).crear(enviado.capture(), eq(USERNAME));
        assertThat(enviado.getValue().getNombre()).isEqualTo("Ventas");
        assertThat(enviado.getValue().getDescripcion()).isEqualTo("Proceso comercial");
        assertThat(enviado.getValue().getCategoria()).isEqualTo("Comercial");
    }
}

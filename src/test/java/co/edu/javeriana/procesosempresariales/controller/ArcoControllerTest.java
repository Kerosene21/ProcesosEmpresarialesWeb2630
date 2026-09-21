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
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.dto.ArcoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearArcoDto;
import co.edu.javeriana.procesosempresariales.dto.EditarArcoDto;
import co.edu.javeriana.procesosempresariales.dto.NodoFlujoDto;
import co.edu.javeriana.procesosempresariales.service.ArcoService;

@ExtendWith(MockitoExtension.class)
class ArcoControllerTest {

    private static final String USERNAME = "admin@alpes.com";
    private static final Principal PRINCIPAL = () -> USERNAME;
    private static final String RUTA_NUEVO = "/procesos/5/arcos/nuevo";
    private static final String RUTA_ARCOS = "/procesos/5/arcos";
    private static final String RUTA_EDITAR = "/procesos/5/arcos/60/editar";
    private static final String RUTA_ARCO = "/procesos/5/arcos/60";
    private static final String RUTA_ELIMINAR = "/procesos/5/arcos/60/eliminar";
    private static final String DETALLE_DEL_PROCESO = "/procesos/5";

    @Mock
    private ArcoService arcoService;

    private MockMvc mockMvc;

    @BeforeEach
    void inicializar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ArcoController(arcoService)).build();
    }

    private ArcoRespuestaDto arco() {
        ArcoRespuestaDto arco = new ArcoRespuestaDto();
        arco.setId(60L);
        arco.setProcesoId(5L);
        arco.setOrigenTipo(TipoNodoFlujo.ACTIVIDAD);
        arco.setOrigenId(30L);
        arco.setOrigenNombre("Revisar solicitud");
        arco.setDestinoTipo(TipoNodoFlujo.ACTIVIDAD);
        arco.setDestinoId(31L);
        arco.setDestinoNombre("Aprobar solicitud");
        arco.setEtiqueta("solicitud completa");
        arco.setActivo(true);
        return arco;
    }

    private void devolverLosNodos() {
        when(arcoService.nodosDelProceso(eq(5L), anyString()))
                .thenReturn(List.of(new NodoFlujoDto(TipoNodoFlujo.ACTIVIDAD, 30L, "Revisar solicitud"),
                        new NodoFlujoDto(TipoNodoFlujo.GATEWAY, 12L, "Gateway EXCLUSIVO #12")));
    }

    private CrearArcoDto arcoCreado() {
        ArgumentCaptor<CrearArcoDto> capturado = ArgumentCaptor.forClass(CrearArcoDto.class);
        verify(arcoService).crear(eq(5L), capturado.capture(), eq(USERNAME));
        return capturado.getValue();
    }

    private EditarArcoDto arcoEditado() {
        ArgumentCaptor<EditarArcoDto> capturado = ArgumentCaptor.forClass(EditarArcoDto.class);
        verify(arcoService).editar(eq(5L), eq(60L), capturado.capture(), eq(USERNAME));
        return capturado.getValue();
    }

    @Test
    void elFormularioDeCreacionOfreceLosNodosDelProceso() throws Exception {
        devolverLosNodos();

        MvcResult resultado = mockMvc.perform(get(RUTA_NUEVO).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(view().name("arcos/formulario"))
                .andExpect(model().attribute("procesoId", 5L))
                .andReturn();

        assertThat(resultado.getModelAndView().getModel().get("arco")).isInstanceOf(CrearArcoDto.class);
        assertThat((TipoNodoFlujo[]) resultado.getModelAndView().getModel().get("tiposDeNodo"))
                .containsExactly(TipoNodoFlujo.ACTIVIDAD, TipoNodoFlujo.GATEWAY);
    }

    @Test
    void crearEnviaElFormularioAlServicioYVuelveAlProceso() throws Exception {
        when(arcoService.crear(eq(5L), any(CrearArcoDto.class), eq(USERNAME))).thenReturn(arco());

        mockMvc.perform(post(RUTA_ARCOS).principal(PRINCIPAL)
                .param("origenTipo", "ACTIVIDAD")
                .param("origenId", "30")
                .param("destinoTipo", "ACTIVIDAD")
                .param("destinoId", "31")
                .param("etiqueta", "solicitud completa"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(DETALLE_DEL_PROCESO))
                .andExpect(flash().attributeExists("mensaje"));

        assertThat(arcoCreado().getOrigenId()).isEqualTo(30L);
        assertThat(arcoCreado().getEtiqueta()).isEqualTo("solicitud completa");
    }

    @Test
    void crearLlevaLasAdvertenciasAlDetalleDelProceso() throws Exception {
        ArcoRespuestaDto creado = arco();
        creado.setAdvertencias(List.of("Gateway EXCLUSIVO #12 tiene 1 arco de salida"));
        when(arcoService.crear(eq(5L), any(CrearArcoDto.class), eq(USERNAME))).thenReturn(creado);

        mockMvc.perform(post(RUTA_ARCOS).principal(PRINCIPAL)
                .param("origenTipo", "GATEWAY")
                .param("origenId", "12")
                .param("destinoTipo", "ACTIVIDAD")
                .param("destinoId", "31")
                .param("condicion", "monto > 100"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("advertencias",
                        List.of("Gateway EXCLUSIVO #12 tiene 1 arco de salida")));
    }

    @Test
    void crearConFormularioIncompletoVuelveAMostrarElFormulario() throws Exception {
        devolverLosNodos();

        mockMvc.perform(post(RUTA_ARCOS).principal(PRINCIPAL)
                .param("origenTipo", "ACTIVIDAD")
                .param("destinoTipo", "ACTIVIDAD")
                .param("destinoId", "31"))
                .andExpect(status().isOk())
                .andExpect(view().name("arcos/formulario"))
                .andExpect(model().attributeHasFieldErrors("arco", "origenId"));

        verify(arcoService, never()).crear(anyLong(), any(CrearArcoDto.class), anyString());
    }

    @Test
    void elFormularioDeEdicionLlegaConLosDatosActuales() throws Exception {
        devolverLosNodos();
        when(arcoService.obtener(5L, 60L, USERNAME)).thenReturn(arco());

        MvcResult resultado = mockMvc.perform(get(RUTA_EDITAR).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(view().name("arcos/formularioeditar"))
                .andExpect(model().attribute("arcoId", 60L))
                .andReturn();

        EditarArcoDto formulario = (EditarArcoDto) resultado.getModelAndView().getModel().get("arco");
        assertThat(formulario.getOrigenId()).isEqualTo(30L);
        assertThat(formulario.getDestinoId()).isEqualTo(31L);
        assertThat(formulario.getEtiqueta()).isEqualTo("solicitud completa");
    }

    @Test
    void actualizarEnviaElFormularioAlServicio() throws Exception {
        when(arcoService.editar(eq(5L), eq(60L), any(EditarArcoDto.class), eq(USERNAME))).thenReturn(arco());

        mockMvc.perform(post(RUTA_ARCO).principal(PRINCIPAL)
                .param("origenTipo", "ACTIVIDAD")
                .param("origenId", "30")
                .param("destinoTipo", "ACTIVIDAD")
                .param("destinoId", "32")
                .param("etiqueta", "otra etiqueta"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(DETALLE_DEL_PROCESO));

        assertThat(arcoEditado().getDestinoId()).isEqualTo(32L);
        assertThat(arcoEditado().getEtiqueta()).isEqualTo("otra etiqueta");
    }

    @Test
    void actualizarConFormularioIncompletoVuelveAMostrarElFormulario() throws Exception {
        devolverLosNodos();

        mockMvc.perform(post(RUTA_ARCO).principal(PRINCIPAL)
                .param("origenTipo", "ACTIVIDAD")
                .param("origenId", "30")
                .param("destinoTipo", "ACTIVIDAD"))
                .andExpect(status().isOk())
                .andExpect(view().name("arcos/formularioeditar"))
                .andExpect(model().attribute("arcoId", 60L));

        verify(arcoService, never()).editar(anyLong(), anyLong(), any(EditarArcoDto.class), anyString());
    }

    @Test
    void laConfirmacionMuestraElArcoYNoLoElimina() throws Exception {
        ArcoRespuestaDto arco = arco();
        arco.setAdvertencias(List.of("'Revisar solicitud' quedó sin arcos de salida"));
        when(arcoService.obtenerParaEliminar(5L, 60L, USERNAME)).thenReturn(arco);

        mockMvc.perform(get(RUTA_ELIMINAR).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(view().name("arcos/confirmareliminacion"))
                .andExpect(model().attribute("procesoId", 5L))
                .andExpect(model().attributeExists("arco"));

        verify(arcoService, never()).eliminar(anyLong(), anyLong(), anyString());
    }

    @Test
    void eliminarEnviaElArcoAlServicioYVuelveAlProceso() throws Exception {
        ArcoRespuestaDto eliminado = arco();
        eliminado.setActivo(false);
        eliminado.setAdvertencias(List.of("'Aprobar solicitud' quedó sin arcos de entrada"));
        when(arcoService.eliminar(5L, 60L, USERNAME)).thenReturn(eliminado);

        mockMvc.perform(post(RUTA_ELIMINAR).principal(PRINCIPAL))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(DETALLE_DEL_PROCESO))
                .andExpect(flash().attributeExists("mensaje"))
                .andExpect(flash().attribute("advertencias",
                        List.of("'Aprobar solicitud' quedó sin arcos de entrada")));

        verify(arcoService).eliminar(5L, 60L, USERNAME);
    }
}

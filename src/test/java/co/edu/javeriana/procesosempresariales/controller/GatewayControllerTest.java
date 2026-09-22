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

import co.edu.javeriana.procesosempresariales.domain.TipoGateway;
import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import co.edu.javeriana.procesosempresariales.dto.ArcoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.CrearGatewayDto;
import co.edu.javeriana.procesosempresariales.dto.EditarGatewayDto;
import co.edu.javeriana.procesosempresariales.dto.GatewayRespuestaDto;
import co.edu.javeriana.procesosempresariales.service.ArcoService;
import co.edu.javeriana.procesosempresariales.service.GatewayService;

@ExtendWith(MockitoExtension.class)
class GatewayControllerTest {

    private static final String USERNAME = "admin@alpes.com";
    private static final Principal PRINCIPAL = () -> USERNAME;
    private static final String RUTA_NUEVO = "/procesos/5/gateways/nuevo";
    private static final String RUTA_GATEWAYS = "/procesos/5/gateways";
    private static final String RUTA_EDITAR = "/procesos/5/gateways/12/editar";
    private static final String RUTA_GATEWAY = "/procesos/5/gateways/12";
    private static final String DETALLE_DEL_PROCESO = "/procesos/5";

    @Mock
    private GatewayService gatewayService;

    @Mock
    private ArcoService arcoService;

    private MockMvc mockMvc;

    @BeforeEach
    void inicializar() {
        mockMvc = MockMvcBuilders.standaloneSetup(new GatewayController(gatewayService, arcoService)).build();
    }

    private GatewayRespuestaDto gateway(TipoGateway tipo) {
        GatewayRespuestaDto gateway = new GatewayRespuestaDto();
        gateway.setId(12L);
        gateway.setProcesoId(5L);
        gateway.setTipo(tipo);
        gateway.setSimbolo(tipo.getSimbolo());
        gateway.setEtiqueta("Gateway " + tipo + " #12");
        gateway.setPosicionX(300);
        gateway.setPosicionY(120);
        gateway.setActivo(true);
        return gateway;
    }

    private ArcoRespuestaDto saliente(Long id, String destino, String condicion) {
        ArcoRespuestaDto arco = new ArcoRespuestaDto();
        arco.setId(id);
        arco.setProcesoId(5L);
        arco.setOrigenTipo(TipoNodoFlujo.GATEWAY);
        arco.setOrigenId(12L);
        arco.setDestinoTipo(TipoNodoFlujo.ACTIVIDAD);
        arco.setDestinoNombre(destino);
        arco.setCondicion(condicion);
        arco.setActivo(true);
        return arco;
    }

    private void devolverLasSalidas() {
        when(arcoService.salientesDe(eq(5L), eq(TipoNodoFlujo.GATEWAY), eq(12L), anyString()))
                .thenReturn(List.of(saliente(61L, "Aprobar solicitud", "monto > 100"),
                        saliente(62L, "Rechazar solicitud", "monto <= 100")));
    }

    private CrearGatewayDto gatewayCreado() {
        ArgumentCaptor<CrearGatewayDto> capturado = ArgumentCaptor.forClass(CrearGatewayDto.class);
        verify(gatewayService).crear(eq(5L), capturado.capture(), eq(USERNAME));
        return capturado.getValue();
    }

    private EditarGatewayDto gatewayEditado() {
        ArgumentCaptor<EditarGatewayDto> capturado = ArgumentCaptor.forClass(EditarGatewayDto.class);
        verify(gatewayService).editar(eq(5L), eq(12L), capturado.capture(), eq(USERNAME));
        return capturado.getValue();
    }

    @Test
    void elFormularioDeCreacionOfreceLosTresTipos() throws Exception {
        MvcResult resultado = mockMvc.perform(get(RUTA_NUEVO).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(view().name("gateways/formulario"))
                .andExpect(model().attribute("procesoId", 5L))
                .andReturn();

        assertThat(resultado.getModelAndView().getModel().get("gateway")).isInstanceOf(CrearGatewayDto.class);
        assertThat((TipoGateway[]) resultado.getModelAndView().getModel().get("tipos"))
                .containsExactly(TipoGateway.EXCLUSIVO, TipoGateway.PARALELO, TipoGateway.INCLUSIVO);
    }

    @Test
    void crearEnviaElFormularioAlServicioYVuelveAlProceso() throws Exception {
        when(gatewayService.crear(eq(5L), any(CrearGatewayDto.class), eq(USERNAME)))
                .thenReturn(gateway(TipoGateway.EXCLUSIVO));

        mockMvc.perform(post(RUTA_GATEWAYS).principal(PRINCIPAL)
                .param("tipo", "EXCLUSIVO")
                .param("posicionX", "300")
                .param("posicionY", "120"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(DETALLE_DEL_PROCESO))
                .andExpect(flash().attributeExists("mensaje"));

        assertThat(gatewayCreado().getTipo()).isEqualTo(TipoGateway.EXCLUSIVO);
        assertThat(gatewayCreado().getPosicionX()).isEqualTo(300);
    }

    @Test
    void crearLlevaLasAdvertenciasAlDetalleDelProceso() throws Exception {
        GatewayRespuestaDto creado = gateway(TipoGateway.EXCLUSIVO);
        creado.setAdvertencias(List.of("Gateway EXCLUSIVO #12 tiene 0 arcos de salida"));
        when(gatewayService.crear(eq(5L), any(CrearGatewayDto.class), eq(USERNAME))).thenReturn(creado);

        mockMvc.perform(post(RUTA_GATEWAYS).principal(PRINCIPAL)
                .param("tipo", "EXCLUSIVO")
                .param("posicionX", "300")
                .param("posicionY", "120"))
                .andExpect(flash().attribute("advertencias",
                        List.of("Gateway EXCLUSIVO #12 tiene 0 arcos de salida")));
    }

    @Test
    void crearConFormularioIncompletoVuelveAMostrarElFormulario() throws Exception {
        mockMvc.perform(post(RUTA_GATEWAYS).principal(PRINCIPAL)
                .param("posicionX", "300"))
                .andExpect(status().isOk())
                .andExpect(view().name("gateways/formulario"))
                .andExpect(model().attributeHasFieldErrors("gateway", "tipo", "posicionY"));

        verify(gatewayService, never()).crear(anyLong(), any(CrearGatewayDto.class), anyString());
    }

    @Test
    void elFormularioDeEdicionLlegaConElTipoYLasCondicionesActuales() throws Exception {
        when(gatewayService.obtener(5L, 12L, USERNAME)).thenReturn(gateway(TipoGateway.EXCLUSIVO));
        devolverLasSalidas();

        MvcResult resultado = mockMvc.perform(get(RUTA_EDITAR).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(view().name("gateways/formularioeditar"))
                .andExpect(model().attribute("gatewayId", 12L))
                .andExpect(model().attributeExists("salientes"))
                .andReturn();

        EditarGatewayDto formulario = (EditarGatewayDto) resultado.getModelAndView().getModel().get("gateway");
        assertThat(formulario.getTipo()).isEqualTo(TipoGateway.EXCLUSIVO);
        assertThat(formulario.getCondiciones()).containsEntry(61L, "monto > 100").containsEntry(62L,
                "monto <= 100");
    }

    @Test
    void actualizarEnviaElTipoYLasCondicionesAlServicio() throws Exception {
        when(gatewayService.editar(eq(5L), eq(12L), any(EditarGatewayDto.class), eq(USERNAME)))
                .thenReturn(gateway(TipoGateway.PARALELO));

        mockMvc.perform(post(RUTA_GATEWAY).principal(PRINCIPAL)
                .param("tipo", "PARALELO")
                .param("condiciones[61]", "monto > 100"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(DETALLE_DEL_PROCESO))
                .andExpect(flash().attributeExists("mensaje"));

        assertThat(gatewayEditado().getTipo()).isEqualTo(TipoGateway.PARALELO);
        assertThat(gatewayEditado().getCondiciones()).containsEntry(61L, "monto > 100");
    }

    @Test
    void actualizarSinTipoVuelveAMostrarElFormulario() throws Exception {
        devolverLasSalidas();

        mockMvc.perform(post(RUTA_GATEWAY).principal(PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(view().name("gateways/formularioeditar"))
                .andExpect(model().attributeHasFieldErrors("gateway", "tipo"));

        verify(gatewayService, never()).editar(anyLong(), anyLong(), any(EditarGatewayDto.class), anyString());
    }
}

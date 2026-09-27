package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import co.edu.javeriana.procesosempresariales.domain.TipoEvento;
import co.edu.javeriana.procesosempresariales.dto.ActividadRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.ArcoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.DiagramaProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EnvioExternoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.FlujoMensajeDto;
import co.edu.javeriana.procesosempresariales.dto.GatewayRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.LaneRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.MessageCatchRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.MessageThrowRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.PoolRespuestaDto;
import co.edu.javeriana.procesosempresariales.exception.UsuarioSinPermisoException;

@ExtendWith(MockitoExtension.class)
class DiagramaProcesoServiceTest {

    private static final String USERNAME = "lector@alpes.com";
    private static final Long PROCESO_ID = 5L;

    @Mock
    private PoolService poolService;

    @Mock
    private ActividadService actividadService;

    @Mock
    private GatewayService gatewayService;

    @Mock
    private ArcoService arcoService;

    @Mock
    private MessageThrowService messageThrowService;

    @Mock
    private MessageCatchService messageCatchService;

    @Mock
    private EnvioExternoService envioExternoService;

    private DiagramaProcesoService diagramaProcesoService;

    @BeforeEach
    void inicializar() {
        diagramaProcesoService = new DiagramaProcesoService(poolService, actividadService, gatewayService,
                arcoService, messageThrowService, messageCatchService, envioExternoService);
    }

    private PoolRespuestaDto pool(Long id) {
        PoolRespuestaDto pool = new PoolRespuestaDto();
        pool.setId(id);
        return pool;
    }

    private MessageThrowRespuestaDto messageThrow() {
        MessageThrowRespuestaDto messageThrow = new MessageThrowRespuestaDto();
        messageThrow.setId(40L);
        messageThrow.setNombreMensaje("Solicitud de pago");
        messageThrow.setPoolOrigenId(80L);
        messageThrow.setPoolDestinoId(90L);
        messageThrow.setCatchHomologoId(41L);
        return messageThrow;
    }

    private MessageCatchRespuestaDto messageCatch() {
        MessageCatchRespuestaDto messageCatch = new MessageCatchRespuestaDto();
        messageCatch.setId(41L);
        return messageCatch;
    }

    private EnvioExternoRespuestaDto envio() {
        EnvioExternoRespuestaDto envio = new EnvioExternoRespuestaDto();
        envio.setId(42L);
        envio.setNombreMensaje("Notificar despacho");
        envio.setPoolOrigenId(80L);
        envio.setPoolDestinoId(95L);
        return envio;
    }

    @Test
    void elDiagramaReuneTodosLosElementosDelProceso() {
        when(poolService.listar(PROCESO_ID, USERNAME)).thenReturn(List.of(pool(80L), pool(90L), pool(95L)));
        when(actividadService.lanesDelProceso(PROCESO_ID, USERNAME)).thenReturn(List.of(new LaneRespuestaDto(11L,
                "General")));
        when(actividadService.consultarActivas(PROCESO_ID, USERNAME)).thenReturn(List.of(new ActividadRespuestaDto()));
        when(gatewayService.consultarActivos(PROCESO_ID, USERNAME)).thenReturn(List.of(new GatewayRespuestaDto()));
        when(arcoService.consultarActivos(PROCESO_ID, USERNAME)).thenReturn(List.of(new ArcoRespuestaDto()));
        when(messageThrowService.listar(PROCESO_ID, USERNAME)).thenReturn(List.of(messageThrow()));
        when(messageCatchService.listar(PROCESO_ID, USERNAME)).thenReturn(List.of(messageCatch()));
        when(envioExternoService.listar(PROCESO_ID, USERNAME)).thenReturn(List.of(envio()));

        DiagramaProcesoDto diagrama = diagramaProcesoService.obtener(PROCESO_ID, USERNAME);

        assertThat(diagrama.getProcesoId()).isEqualTo(PROCESO_ID);
        assertThat(diagrama.getPools()).hasSize(3);
        assertThat(diagrama.getLanes()).hasSize(1);
        assertThat(diagrama.getActividades()).hasSize(1);
        assertThat(diagrama.getGateways()).hasSize(1);
        assertThat(diagrama.getArcos()).hasSize(1);
        assertThat(diagrama.getMessageThrows()).extracting(MessageThrowRespuestaDto::getId).containsExactly(40L);
        assertThat(diagrama.getMessageCatches()).extracting(MessageCatchRespuestaDto::getId).containsExactly(41L);
        assertThat(diagrama.getEnviosExternos()).extracting(EnvioExternoRespuestaDto::getId).containsExactly(42L);
        assertThat(diagrama.getFlujosMensaje()).hasSize(2);
        FlujoMensajeDto flujoDelThrow = diagrama.getFlujosMensaje().get(0);
        assertThat(flujoDelThrow.getTipoEvento()).isEqualTo(TipoEvento.MESSAGE_THROW);
        assertThat(flujoDelThrow.getEventoId()).isEqualTo(40L);
        assertThat(flujoDelThrow.getNombreMensaje()).isEqualTo("Solicitud de pago");
        assertThat(flujoDelThrow.getPoolOrigenId()).isEqualTo(80L);
        assertThat(flujoDelThrow.getPoolDestinoId()).isEqualTo(90L);
        assertThat(flujoDelThrow.getCatchDestinoId()).isEqualTo(41L);
        FlujoMensajeDto flujoDelEnvio = diagrama.getFlujosMensaje().get(1);
        assertThat(flujoDelEnvio.getTipoEvento()).isEqualTo(TipoEvento.ENVIO_EXTERNO);
        assertThat(flujoDelEnvio.getPoolDestinoId()).isEqualTo(95L);
        assertThat(flujoDelEnvio.getCatchDestinoId()).isNull();
    }

    @Test
    void unDiagramaSinMensajesNoTieneFlujosDeMensaje() {
        when(poolService.listar(PROCESO_ID, USERNAME)).thenReturn(List.of(pool(80L)));

        DiagramaProcesoDto diagrama = diagramaProcesoService.obtener(PROCESO_ID, USERNAME);

        assertThat(diagrama.getMessageThrows()).isEmpty();
        assertThat(diagrama.getFlujosMensaje()).isEmpty();
    }

    @Test
    void elDiagramaRespetaElAccesoAlProceso() {
        when(poolService.listar(PROCESO_ID, USERNAME))
                .thenThrow(new UsuarioSinPermisoException("El proceso no pertenece a la empresa del usuario"));

        assertThatThrownBy(() -> diagramaProcesoService.obtener(PROCESO_ID, USERNAME))
                .isInstanceOf(UsuarioSinPermisoException.class);

        verify(messageThrowService, never()).listar(PROCESO_ID, USERNAME);
    }
}

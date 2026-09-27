package co.edu.javeriana.procesosempresariales.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.javeriana.procesosempresariales.domain.TipoEvento;
import co.edu.javeriana.procesosempresariales.dto.DiagramaProcesoDto;
import co.edu.javeriana.procesosempresariales.dto.EnvioExternoRespuestaDto;
import co.edu.javeriana.procesosempresariales.dto.FlujoMensajeDto;
import co.edu.javeriana.procesosempresariales.dto.MessageThrowRespuestaDto;

@Service
public class DiagramaProcesoService {

    private PoolService poolService;
    private ActividadService actividadService;
    private GatewayService gatewayService;
    private ArcoService arcoService;
    private MessageThrowService messageThrowService;
    private MessageCatchService messageCatchService;
    private EnvioExternoService envioExternoService;

    @Autowired
    public DiagramaProcesoService(PoolService poolService, ActividadService actividadService,
            GatewayService gatewayService, ArcoService arcoService, MessageThrowService messageThrowService,
            MessageCatchService messageCatchService, EnvioExternoService envioExternoService) {
        this.poolService = poolService;
        this.actividadService = actividadService;
        this.gatewayService = gatewayService;
        this.arcoService = arcoService;
        this.messageThrowService = messageThrowService;
        this.messageCatchService = messageCatchService;
        this.envioExternoService = envioExternoService;
    }

    @Transactional(readOnly = true)
    public DiagramaProcesoDto obtener(Long procesoId, String username) {
        DiagramaProcesoDto diagrama = new DiagramaProcesoDto();
        diagrama.setProcesoId(procesoId);
        diagrama.setPools(poolService.listar(procesoId, username));
        diagrama.setLanes(actividadService.lanesDelProceso(procesoId, username));
        diagrama.setActividades(actividadService.consultarActivas(procesoId, username));
        diagrama.setGateways(gatewayService.consultarActivos(procesoId, username));
        diagrama.setArcos(arcoService.consultarActivos(procesoId, username));
        diagrama.setMessageThrows(messageThrowService.listar(procesoId, username));
        diagrama.setMessageCatches(messageCatchService.listar(procesoId, username));
        diagrama.setEnviosExternos(envioExternoService.listar(procesoId, username));
        diagrama.setFlujosMensaje(flujosDeMensaje(diagrama.getMessageThrows(), diagrama.getEnviosExternos()));
        return diagrama;
    }

    private List<FlujoMensajeDto> flujosDeMensaje(List<MessageThrowRespuestaDto> messageThrows,
            List<EnvioExternoRespuestaDto> enviosExternos) {
        List<FlujoMensajeDto> flujos = new ArrayList<>();
        for (MessageThrowRespuestaDto messageThrow : messageThrows) {
            flujos.add(new FlujoMensajeDto(TipoEvento.MESSAGE_THROW, messageThrow.getId(),
                    messageThrow.getNombreMensaje(), messageThrow.getPoolOrigenId(), messageThrow.getPoolDestinoId(),
                    messageThrow.getCatchHomologoId()));
        }
        for (EnvioExternoRespuestaDto envio : enviosExternos) {
            flujos.add(new FlujoMensajeDto(TipoEvento.ENVIO_EXTERNO, envio.getId(), envio.getNombreMensaje(),
                    envio.getPoolOrigenId(), envio.getPoolDestinoId(), null));
        }
        return flujos;
    }
}

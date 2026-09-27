package co.edu.javeriana.procesosempresariales.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class DiagramaProcesoDto {
    private Long procesoId;
    private List<PoolRespuestaDto> pools = new ArrayList<>();
    private List<LaneRespuestaDto> lanes = new ArrayList<>();
    private List<ActividadRespuestaDto> actividades = new ArrayList<>();
    private List<GatewayRespuestaDto> gateways = new ArrayList<>();
    private List<ArcoRespuestaDto> arcos = new ArrayList<>();
    private List<MessageThrowRespuestaDto> messageThrows = new ArrayList<>();
    private List<MessageCatchRespuestaDto> messageCatches = new ArrayList<>();
    private List<EnvioExternoRespuestaDto> enviosExternos = new ArrayList<>();
    private List<FlujoMensajeDto> flujosMensaje = new ArrayList<>();
}

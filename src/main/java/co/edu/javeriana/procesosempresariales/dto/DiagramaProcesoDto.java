package co.edu.javeriana.procesosempresariales.dto;

import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Schema(description = "Modelo BPMN del proceso para dibujar el diagrama. Solo representa el modelo; no hay ejecución.")
@Getter @Setter
public class DiagramaProcesoDto {
    private Long procesoId;
    private List<PoolRespuestaDto> pools = new ArrayList<>();
    private List<LaneRespuestaDto> lanes = new ArrayList<>();
    private List<ActividadRespuestaDto> actividades = new ArrayList<>();
    private List<GatewayRespuestaDto> gateways = new ArrayList<>();
    @Schema(description = "Flujos de secuencia entre nodos de un mismo pool.")
    private List<ArcoRespuestaDto> arcos = new ArrayList<>();
    private List<MessageThrowRespuestaDto> messageThrows = new ArrayList<>();
    private List<MessageCatchRespuestaDto> messageCatches = new ArrayList<>();
    private List<EnvioExternoRespuestaDto> enviosExternos = new ArrayList<>();
    @Schema(description = "Flujos de mensaje entre pools, derivados de los Message Throw y de los envíos externos.")
    private List<FlujoMensajeDto> flujosMensaje = new ArrayList<>();
}

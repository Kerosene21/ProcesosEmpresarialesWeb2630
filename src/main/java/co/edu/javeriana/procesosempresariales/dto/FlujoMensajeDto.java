package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.TipoEvento;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "Flujo de mensaje entre dos pools del diagrama.")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class FlujoMensajeDto {
    private TipoEvento tipoEvento;
    private Long eventoId;
    private String nombreMensaje;
    private Long poolOrigenId;
    private Long poolDestinoId;
    @Schema(description = "Message Catch homólogo que recibe el mensaje; null en envíos externos o si no hay "
            + "homólogo.")
    private Long catchDestinoId;
}

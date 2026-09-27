package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.TipoEvento;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class FlujoMensajeDto {
    private TipoEvento tipoEvento;
    private Long eventoId;
    private String nombreMensaje;
    private Long poolOrigenId;
    private Long poolDestinoId;
    private Long catchDestinoId;
}

package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.TipoGateway;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CrearGatewayDto {

    @NotNull(message = "El tipo de gateway es obligatorio")
    private TipoGateway tipo;

    @NotNull(message = "La posición X es obligatoria")
    private Integer posicionX;

    @NotNull(message = "La posición Y es obligatoria")
    private Integer posicionY;

    private Long poolId;

    public CrearGatewayDto(TipoGateway tipo, Integer posicionX, Integer posicionY) {
        this(tipo, posicionX, posicionY, null);
    }
}

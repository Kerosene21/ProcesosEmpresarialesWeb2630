package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.TipoGateway;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(description = "Pool del elemento; obligatorio si el proceso tiene más de un pool activo.")
    private Long poolId;

    public CrearGatewayDto(TipoGateway tipo, Integer posicionX, Integer posicionY) {
        this(tipo, posicionX, posicionY, null);
    }
}

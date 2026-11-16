package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PosicionNodoDto {

    @NotNull(message = "El tipo de nodo es obligatorio")
    private TipoNodoFlujo tipo;

    @NotNull(message = "El identificador del nodo es obligatorio")
    private Long id;

    @NotNull(message = "La posición X es obligatoria")
    @Min(value = 0, message = "La posición X no puede ser negativa")
    @Max(value = MoverNodoDto.POSICION_MAXIMA, message = "La posición X no puede superar 50000")
    private Integer posicionX;

    @NotNull(message = "La posición Y es obligatoria")
    @Min(value = 0, message = "La posición Y no puede ser negativa")
    @Max(value = MoverNodoDto.POSICION_MAXIMA, message = "La posición Y no puede superar 50000")
    private Integer posicionY;
}

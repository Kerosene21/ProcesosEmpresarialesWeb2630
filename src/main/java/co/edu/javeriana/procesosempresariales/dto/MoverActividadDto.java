package co.edu.javeriana.procesosempresariales.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class MoverActividadDto {

    @NotNull(message = "La lane responsable es obligatoria")
    private Long laneId;

    @NotNull(message = "La posición X es obligatoria")
    @Min(value = 0, message = "La posición X no puede ser negativa")
    @Max(value = MoverNodoDto.POSICION_MAXIMA, message = "La posición X no puede superar 50000")
    private Integer posicionX;

    @NotNull(message = "La posición Y es obligatoria")
    @Min(value = 0, message = "La posición Y no puede ser negativa")
    @Max(value = MoverNodoDto.POSICION_MAXIMA, message = "La posición Y no puede superar 50000")
    private Integer posicionY;
}

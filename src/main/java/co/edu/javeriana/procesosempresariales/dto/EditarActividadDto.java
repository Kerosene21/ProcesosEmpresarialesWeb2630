package co.edu.javeriana.procesosempresariales.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import co.edu.javeriana.procesosempresariales.domain.TipoActividad;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class EditarActividadDto {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
    private String nombre;

    @NotNull(message = "El tipo de actividad es obligatorio")
    private TipoActividad tipo;

    @NotNull(message = "La lane responsable es obligatoria")
    private Long laneId;

    @Schema(description = "Nueva posición X; opcional, pero obligatoria junto con posicionY. Permite cambiar de lane "
            + "y recolocar la actividad dentro de ella en la misma operación.")
    @Min(value = 0, message = "La posición X no puede ser negativa")
    @Max(value = MoverNodoDto.POSICION_MAXIMA, message = "La posición X no puede superar 50000")
    private Integer posicionX;

    @Schema(description = "Nueva posición Y; opcional, pero obligatoria junto con posicionX.")
    @Min(value = 0, message = "La posición Y no puede ser negativa")
    @Max(value = MoverNodoDto.POSICION_MAXIMA, message = "La posición Y no puede superar 50000")
    private Integer posicionY;

    public EditarActividadDto(String nombre, TipoActividad tipo, Long laneId) {
        this(nombre, tipo, laneId, null, null);
    }

    @JsonIgnore
    @AssertTrue(message = "Indica a la vez la posición X y la posición Y, o ninguna de las dos")
    public boolean isPosicionCompleta() {
        return (posicionX == null) == (posicionY == null);
    }

    public boolean cambiaPosicion() {
        return posicionX != null && posicionY != null;
    }
}

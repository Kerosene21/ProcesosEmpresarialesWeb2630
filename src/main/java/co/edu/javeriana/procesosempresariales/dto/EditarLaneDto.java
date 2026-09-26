package co.edu.javeriana.procesosempresariales.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class EditarLaneDto {

    @NotNull(message = "El rol de proceso de la lane es obligatorio")
    private Long rolProcesoId;

    @Min(value = 1, message = "El orden de la lane empieza en 1")
    private Integer orden;
}

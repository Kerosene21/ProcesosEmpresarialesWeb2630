package co.edu.javeriana.procesosempresariales.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CrearLaneDto {

    @NotNull(message = "El rol de proceso de la lane es obligatorio")
    private Long rolProcesoId;

    @Schema(description = "Posición de la lane en el pool, desde 1. Si se omite, se agrega al final.")
    @Min(value = 1, message = "El orden de la lane empieza en 1")
    private Integer orden;
}

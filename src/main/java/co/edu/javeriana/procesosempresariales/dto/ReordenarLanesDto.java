package co.edu.javeriana.procesosempresariales.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ReordenarLanesDto {

    @Schema(description = "Ids de todas las lanes activas del pool en el nuevo orden.")
    @NotEmpty(message = "Indica el nuevo orden de las lanes")
    private List<@NotNull(message = "El identificador de la lane es obligatorio") Long> lanes;
}

package co.edu.javeriana.procesosempresariales.dto;

import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RecolocarNodosDto {

    @Schema(description = "Nodos del proceso y su nueva posición. Se aplican todos o ninguno.")
    @NotEmpty(message = "Indica al menos un nodo para recolocar")
    @Size(max = 500, message = "No se pueden recolocar más de 500 nodos a la vez")
    private List<@Valid PosicionNodoDto> nodos = new ArrayList<>();
}

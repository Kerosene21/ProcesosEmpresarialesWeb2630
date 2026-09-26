package co.edu.javeriana.procesosempresariales.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ReordenarLanesDto {

    @NotEmpty(message = "Indica el nuevo orden de las lanes")
    private List<@NotNull(message = "El identificador de la lane es obligatorio") Long> lanes;
}

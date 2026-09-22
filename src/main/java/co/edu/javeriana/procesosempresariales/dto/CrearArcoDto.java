package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CrearArcoDto {

    @NotNull(message = "El tipo del nodo origen es obligatorio")
    private TipoNodoFlujo origenTipo;

    @NotNull(message = "El nodo origen es obligatorio")
    private Long origenId;

    @NotNull(message = "El tipo del nodo destino es obligatorio")
    private TipoNodoFlujo destinoTipo;

    @NotNull(message = "El nodo destino es obligatorio")
    private Long destinoId;

    @Size(max = 150, message = "La etiqueta no puede superar 150 caracteres")
    private String etiqueta;

    @Size(max = 200, message = "La condición no puede superar 200 caracteres")
    private String condicion;
}

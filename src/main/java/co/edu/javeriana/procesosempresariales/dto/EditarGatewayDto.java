package co.edu.javeriana.procesosempresariales.dto;

import java.util.LinkedHashMap;
import java.util.Map;

import co.edu.javeriana.procesosempresariales.domain.TipoGateway;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class EditarGatewayDto {

    @NotNull(message = "El tipo de gateway es obligatorio")
    private TipoGateway tipo;

    private Map<Long, String> condiciones = new LinkedHashMap<>();
}

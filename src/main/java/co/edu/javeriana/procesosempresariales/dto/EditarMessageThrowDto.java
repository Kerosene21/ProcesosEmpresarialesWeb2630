package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.ComportamientoSinCaso;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class EditarMessageThrowDto {

    @Schema(example = "FacturaAprobada")
    @NotBlank(message = "El nombre del mensaje es obligatorio")
    @Size(max = 150, message = "El nombre del mensaje no puede superar 150 caracteres")
    private String nombreMensaje;

    @Schema(description = "Datos que transporta el mensaje.", example = "numeroFactura, valorTotal")
    @NotBlank(message = "El contenido del mensaje es obligatorio")
    @Size(max = 2000, message = "El contenido del mensaje no puede superar 2000 caracteres")
    private String contenido;

    @Schema(description = "Pool activo del proceso que recibe el mensaje; distinto del pool origen.")
    @NotNull(message = "El pool destino es obligatorio")
    private Long poolDestinoId;

    @Schema(description = "Dato que relaciona el mensaje con el caso que lo espera; debe coincidir con la del Message "
            + "Catch homólogo.", example = "numeroRadicado")
    @Size(max = 100, message = "La clave de correlación no puede superar 100 caracteres")
    private String claveCorrelacion;

    @Schema(description = "Qué debe hacer el receptor si el mensaje llega sin un caso en espera.")
    private ComportamientoSinCaso comportamientoSinCaso;
}

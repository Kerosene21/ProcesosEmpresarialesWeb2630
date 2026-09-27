package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.ComportamientoSinCaso;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CrearMessageThrowDto {

    @NotBlank(message = "El nombre del mensaje es obligatorio")
    @Size(max = 150, message = "El nombre del mensaje no puede superar 150 caracteres")
    private String nombreMensaje;

    @NotBlank(message = "El contenido del mensaje es obligatorio")
    @Size(max = 2000, message = "El contenido del mensaje no puede superar 2000 caracteres")
    private String contenido;

    @NotNull(message = "El pool de origen es obligatorio")
    private Long poolOrigenId;

    @NotNull(message = "El pool destino es obligatorio")
    private Long poolDestinoId;

    @Size(max = 100, message = "La clave de correlación no puede superar 100 caracteres")
    private String claveCorrelacion;

    private ComportamientoSinCaso comportamientoSinCaso;

    @NotNull(message = "La posición X es obligatoria")
    private Integer posicionX;

    @NotNull(message = "La posición Y es obligatoria")
    private Integer posicionY;
}

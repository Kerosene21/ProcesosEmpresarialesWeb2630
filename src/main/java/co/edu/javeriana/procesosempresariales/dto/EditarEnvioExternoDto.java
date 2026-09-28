package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.ComportamientoFallo;
import co.edu.javeriana.procesosempresariales.domain.TipoDestinoExterno;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class EditarEnvioExternoDto {

    @NotBlank(message = "El nombre del envío es obligatorio")
    @Size(max = 150, message = "El nombre del envío no puede superar 150 caracteres")
    private String nombreMensaje;

    @NotNull(message = "El pool del sistema externo es obligatorio")
    private Long poolDestinoId;

    @NotNull(message = "El tipo de destino es obligatorio")
    private TipoDestinoExterno tipoDestino;

    @NotBlank(message = "Los datos enviados son obligatorios")
    @Size(max = 2000, message = "Los datos enviados no pueden superar 2000 caracteres")
    private String datosEnviados;

    @NotBlank(message = "El momento del proceso es obligatorio")
    @Size(max = 300, message = "El momento del proceso no puede superar 300 caracteres")
    private String momentoProceso;

    @NotNull(message = "El comportamiento ante fallo es obligatorio")
    private ComportamientoFallo comportamientoFallo;

    @Size(max = 100, message = "La clave de correlación no puede superar 100 caracteres")
    private String claveCorrelacion;
}

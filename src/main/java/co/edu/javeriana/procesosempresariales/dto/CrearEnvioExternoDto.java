package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.ComportamientoFallo;
import co.edu.javeriana.procesosempresariales.domain.TipoDestinoExterno;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "Envío externo a modelar hacia un pool EXTERNO caja negra. Solo se documenta: no se hace "
        + "ninguna llamada real ni se guardan credenciales.")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CrearEnvioExternoDto {

    @Schema(example = "NotificarDespacho")
    @NotBlank(message = "El nombre del envío es obligatorio")
    @Size(max = 150, message = "El nombre del envío no puede superar 150 caracteres")
    private String nombreMensaje;

    @Schema(description = "Pool del proceso que realiza el envío.")
    @NotNull(message = "El pool de origen es obligatorio")
    private Long poolOrigenId;

    @Schema(description = "Pool EXTERNO caja negra que representa al sistema externo.")
    @NotNull(message = "El pool del sistema externo es obligatorio")
    private Long poolDestinoId;

    @Schema(description = "Medio por el que se enviaría: CORREO, SERVICIO_WEB o COLA. Solo se documenta.")
    @NotNull(message = "El tipo de destino es obligatorio")
    private TipoDestinoExterno tipoDestino;

    @Schema(description = "Datos que se enviarían al sistema externo.", example = "numeroGuia, fechaDespacho")
    @NotBlank(message = "Los datos enviados son obligatorios")
    @Size(max = 2000, message = "Los datos enviados no pueden superar 2000 caracteres")
    private String datosEnviados;

    @Schema(description = "Momento del proceso en que ocurre el envío.", example = "Al confirmar el despacho")
    @NotBlank(message = "El momento del proceso es obligatorio")
    @Size(max = 300, message = "El momento del proceso no puede superar 300 caracteres")
    private String momentoProceso;

    @Schema(description = "Qué hace el proceso si el envío falla: CONTINUAR, seguir una RUTA_ERROR o FINALIZAR.")
    @NotNull(message = "El comportamiento ante fallo es obligatorio")
    private ComportamientoFallo comportamientoFallo;

    @Schema(example = "numeroRadicado")
    @Size(max = 100, message = "La clave de correlación no puede superar 100 caracteres")
    private String claveCorrelacion;

    @NotNull(message = "La posición X es obligatoria")
    private Integer posicionX;

    @NotNull(message = "La posición Y es obligatoria")
    private Integer posicionY;
}

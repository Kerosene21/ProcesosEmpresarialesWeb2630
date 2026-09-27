package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.ComportamientoSinCaso;
import co.edu.javeriana.procesosempresariales.domain.VarianteMessageCatch;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "Message Catch a modelar: recepción de un mensaje en un pool. No se recibe ningún mensaje "
        + "real.")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CrearMessageCatchDto {

    @Schema(example = "FacturaAprobada")
    @NotBlank(message = "El nombre del mensaje esperado es obligatorio")
    @Size(max = 150, message = "El nombre del mensaje no puede superar 150 caracteres")
    private String nombreMensaje;

    @Schema(description = "INICIO: el mensaje inicia un caso nuevo. INTERMEDIO: un caso en curso espera el mensaje.")
    @NotNull(message = "La variante del Message Catch es obligatoria")
    private VarianteMessageCatch variante;

    @Schema(description = "Datos que se esperan en el mensaje.", example = "numeroFactura, valorTotal")
    @NotBlank(message = "Los datos esperados son obligatorios")
    @Size(max = 2000, message = "Los datos esperados no pueden superar 2000 caracteres")
    private String datosEsperados;

    @Schema(description = "Actividades del proceso que usan los datos recibidos.", example = "Registrar pago")
    @NotBlank(message = "Las actividades que usan los datos son obligatorias")
    @Size(max = 1000, message = "Las actividades que usan los datos no pueden superar 1000 caracteres")
    private String actividadesUso;

    @Schema(description = "Indica que el mensaje llega desde fuera del proceso, por lo que no se espera un Message "
            + "Throw homólogo.")
    private Boolean origenExterno;

    @Schema(description = "Dato que identifica el caso que espera el mensaje; debe coincidir con la del Message Throw "
            + "homólogo.", example = "numeroRadicado")
    @Size(max = 100, message = "La clave de correlación no puede superar 100 caracteres")
    private String claveCorrelacion;

    @Schema(description = "Qué ocurre si llega un mensaje sin caso en espera. En la variante INICIO siempre es "
            + "INICIAR_NUEVO_CASO.")
    private ComportamientoSinCaso comportamientoSinCaso;

    @Schema(description = "Pool que recibe el mensaje; obligatorio si el proceso tiene más de un pool activo.")
    private Long poolId;

    @NotNull(message = "La posición X es obligatoria")
    private Integer posicionX;

    @NotNull(message = "La posición Y es obligatoria")
    private Integer posicionY;

    public boolean esOrigenExterno() {
        return Boolean.TRUE.equals(origenExterno);
    }
}

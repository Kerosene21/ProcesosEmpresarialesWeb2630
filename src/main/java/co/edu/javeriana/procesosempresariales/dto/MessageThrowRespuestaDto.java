package co.edu.javeriana.procesosempresariales.dto;

import java.util.ArrayList;
import java.util.List;

import co.edu.javeriana.procesosempresariales.domain.ComportamientoSinCaso;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class MessageThrowRespuestaDto {
    private Long id;
    private Long procesoId;
    private String nombreMensaje;
    private String etiqueta;
    private String contenido;
    private Long poolOrigenId;
    private String poolOrigenNombre;
    private Long poolDestinoId;
    private String poolDestinoNombre;
    private String claveCorrelacion;
    private ComportamientoSinCaso comportamientoSinCaso;
    @Schema(description = "Message Catch del pool destino con el mismo nombre y clave de correlación; null si no "
            + "existe.")
    private Long catchHomologoId;
    private Integer posicionX;
    private Integer posicionY;
    private boolean activo;
    private int arcosDesactivados;
    @Schema(description = "Advertencias de correlación y conexiones del modelo; no impiden guardar.")
    private List<String> advertencias = new ArrayList<>();
}

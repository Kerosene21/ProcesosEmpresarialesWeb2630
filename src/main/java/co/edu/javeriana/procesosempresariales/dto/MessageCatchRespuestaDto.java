package co.edu.javeriana.procesosempresariales.dto;

import java.util.ArrayList;
import java.util.List;

import co.edu.javeriana.procesosempresariales.domain.ComportamientoSinCaso;
import co.edu.javeriana.procesosempresariales.domain.VarianteMessageCatch;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class MessageCatchRespuestaDto {
    private Long id;
    private Long procesoId;
    private String nombreMensaje;
    private String etiqueta;
    private VarianteMessageCatch variante;
    private String datosEsperados;
    private String actividadesUso;
    private Long poolId;
    private String poolNombre;
    private boolean origenExterno;
    private String claveCorrelacion;
    private ComportamientoSinCaso comportamientoSinCaso;
    @Schema(description = "Message Throw dirigido a este pool con el mismo nombre y clave de correlación; null si no "
            + "existe.")
    private Long throwHomologoId;
    private Integer posicionX;
    private Integer posicionY;
    private boolean activo;
    private int arcosDesactivados;
    @Schema(description = "Advertencias de correlación y conexiones del modelo; no impiden guardar.")
    private List<String> advertencias = new ArrayList<>();
}

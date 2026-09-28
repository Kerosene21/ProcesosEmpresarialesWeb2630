package co.edu.javeriana.procesosempresariales.dto;

import java.util.ArrayList;
import java.util.List;

import co.edu.javeriana.procesosempresariales.domain.ComportamientoSinCaso;
import co.edu.javeriana.procesosempresariales.domain.VarianteMessageCatch;
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
    private Long throwHomologoId;
    private Integer posicionX;
    private Integer posicionY;
    private boolean activo;
    private int arcosDesactivados;
    private List<String> advertencias = new ArrayList<>();
}

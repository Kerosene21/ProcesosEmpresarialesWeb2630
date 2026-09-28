package co.edu.javeriana.procesosempresariales.dto;

import java.util.ArrayList;
import java.util.List;

import co.edu.javeriana.procesosempresariales.domain.ComportamientoFallo;
import co.edu.javeriana.procesosempresariales.domain.TipoDestinoExterno;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class EnvioExternoRespuestaDto {
    private Long id;
    private Long procesoId;
    private String nombreMensaje;
    private String etiqueta;
    private Long poolOrigenId;
    private String poolOrigenNombre;
    private Long poolDestinoId;
    private String poolDestinoNombre;
    private TipoDestinoExterno tipoDestino;
    private String datosEnviados;
    private String momentoProceso;
    private ComportamientoFallo comportamientoFallo;
    private String claveCorrelacion;
    private Integer posicionX;
    private Integer posicionY;
    private boolean activo;
    private int arcosDesactivados;
    private List<String> advertencias = new ArrayList<>();
}

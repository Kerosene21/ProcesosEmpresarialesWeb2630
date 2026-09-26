package co.edu.javeriana.procesosempresariales.dto;

import java.util.ArrayList;
import java.util.List;

import co.edu.javeriana.procesosempresariales.domain.TipoGateway;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class GatewayRespuestaDto {
    private Long id;
    private Long procesoId;
    private TipoGateway tipo;
    private String simbolo;
    private String etiqueta;
    private Integer posicionX;
    private Integer posicionY;
    private boolean activo;
    private int arcosDesactivados;
    private List<String> advertencias = new ArrayList<>();
}

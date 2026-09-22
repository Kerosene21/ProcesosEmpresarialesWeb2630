package co.edu.javeriana.procesosempresariales.dto;

import java.util.ArrayList;
import java.util.List;

import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ArcoRespuestaDto {
    private Long id;
    private Long procesoId;
    private TipoNodoFlujo origenTipo;
    private Long origenId;
    private String origenNombre;
    private TipoNodoFlujo destinoTipo;
    private Long destinoId;
    private String destinoNombre;
    private String etiqueta;
    private String condicion;
    private boolean activo;
    private Integer origenX;
    private Integer origenY;
    private Integer destinoX;
    private Integer destinoY;
    private List<String> advertencias = new ArrayList<>();
}

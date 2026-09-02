package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import lombok.Getter;
import lombok.Setter;

// Datos que se mandan al cliente 
@Getter @Setter 
public class ProcesoRespuestaDto {
    private Long id; 
    private String nombre; 
    private String descripcion; 
    private String categoria; 
    private EstadoProceso estado; 
    private Long poolId; // Pool asociado al diagrama
}
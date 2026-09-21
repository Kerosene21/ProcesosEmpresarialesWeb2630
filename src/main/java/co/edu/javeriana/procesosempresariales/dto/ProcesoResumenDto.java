package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ProcesoResumenDto {
    private Long id;
    private String nombre;
    private String descripcion;
    private String categoria;
    private EstadoProceso estado;
    private boolean eliminado;
}

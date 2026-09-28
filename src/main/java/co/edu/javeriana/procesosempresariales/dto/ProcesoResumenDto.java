package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import io.swagger.v3.oas.annotations.media.Schema;
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
    private Long empresaPropietariaId;
    private String empresaPropietariaNombre;
    @Schema(description = "true cuando el proceso es de otra empresa y se consulta porque fue compartido.")
    private boolean soloLectura;
}

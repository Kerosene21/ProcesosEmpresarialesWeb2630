package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class FiltroProcesosDto {
    @Schema(description = "Texto contenido en el nombre del proceso.")
    private String q;
    private EstadoProceso estado;
    @Schema(description = "Categoría exacta, sin distinguir mayúsculas.")
    private String categoria;
    @Schema(description = "ACTIVOS (por defecto), INACTIVOS (eliminados) o TODOS.")
    private VisibilidadProceso visibilidad;
    @Schema(description = "PROPIOS (por defecto), COMPARTIDOS con la empresa del usuario o TODOS.")
    private AlcanceProceso alcance;
    @Schema(description = "Número de página desde 0; cada página tiene 10 procesos.")
    private Integer page;
}

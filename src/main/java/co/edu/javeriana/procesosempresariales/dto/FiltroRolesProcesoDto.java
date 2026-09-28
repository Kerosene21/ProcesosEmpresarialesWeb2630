package co.edu.javeriana.procesosempresariales.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class FiltroRolesProcesoDto {
    @Schema(description = "Texto contenido en el nombre del rol.")
    private String q;
    @Schema(description = "ACTIVOS (por defecto), INACTIVOS (eliminados) o TODOS.")
    private VisibilidadRolProceso visibilidad;
    @Schema(description = "Número de página desde 0; cada página tiene 10 roles.")
    private Integer page;
}

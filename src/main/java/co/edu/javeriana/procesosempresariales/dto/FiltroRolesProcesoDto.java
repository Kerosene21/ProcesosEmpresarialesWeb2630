package co.edu.javeriana.procesosempresariales.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class FiltroRolesProcesoDto {
    private String q;
    private VisibilidadRolProceso visibilidad;
    private Integer page;
}

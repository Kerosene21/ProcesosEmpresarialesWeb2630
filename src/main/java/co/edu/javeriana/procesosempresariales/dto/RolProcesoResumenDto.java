package co.edu.javeriana.procesosempresariales.dto;

import java.util.ArrayList;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RolProcesoResumenDto {
    private Long id;
    private String nombre;
    private String descripcion;
    private boolean activo;
    private boolean enUso;
    @Schema(description = "true si el usuario es ADMINISTRADOR y el rol está activo y sin uso.")
    private boolean puedeEliminar;
    private List<ProcesoUsoRolDto> procesos = new ArrayList<>();
}

package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PermisoEstructuraDto {
    private RolUsuario rol;
    @Schema(description = "Solo el rol EDITOR es configurable.")
    private boolean configurable;
    private boolean crearPool;
    private boolean editarPool;
    private boolean eliminarPool;
    private boolean crearLane;
    private boolean editarLane;
    private boolean eliminarLane;
}

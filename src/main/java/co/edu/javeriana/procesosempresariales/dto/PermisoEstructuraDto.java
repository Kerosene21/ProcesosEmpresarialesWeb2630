package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PermisoEstructuraDto {
    private RolUsuario rol;
    private boolean configurable;
    private boolean crearPool;
    private boolean editarPool;
    private boolean eliminarPool;
    private boolean crearLane;
    private boolean editarLane;
    private boolean eliminarLane;
}

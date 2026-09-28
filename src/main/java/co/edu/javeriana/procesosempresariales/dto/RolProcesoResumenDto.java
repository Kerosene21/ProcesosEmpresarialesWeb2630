package co.edu.javeriana.procesosempresariales.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RolProcesoResumenDto {
    private Long id;
    private String nombre;
    private String descripcion;
    private boolean activo;
    private boolean enUso;
    private boolean puedeEliminar;
    private List<ProcesoUsoRolDto> procesos = new ArrayList<>();
}

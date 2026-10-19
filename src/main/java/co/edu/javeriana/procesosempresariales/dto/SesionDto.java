package co.edu.javeriana.procesosempresariales.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import co.edu.javeriana.procesosempresariales.domain.RolUsuario;

@Schema(description = "Estado de la sesión del navegador. Si `autenticado` es false, el resto de campos es null.")
@Getter @Setter
public class SesionDto {
    private boolean autenticado;
    private String correo;
    private RolUsuario rol;
    private Long empresaId;
    private String empresaNombre;
}

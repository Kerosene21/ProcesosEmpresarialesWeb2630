package co.edu.javeriana.procesosempresariales.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import co.edu.javeriana.procesosempresariales.domain.RolUsuario;

@Schema(description = "Usuario autenticado según la base de datos: correo, rol de acceso y empresa actuales.")
@Getter @Setter
public class SesionDto {
    private boolean autenticado;
    private String correo;
    private RolUsuario rol;
    private Long empresaId;
    private String empresaNombre;
}

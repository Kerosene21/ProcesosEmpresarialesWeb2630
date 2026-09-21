package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UsuarioRespuestaDto {
    private Long id;
    private String correo;
    private RolUsuario rol;
    private boolean activo;
}

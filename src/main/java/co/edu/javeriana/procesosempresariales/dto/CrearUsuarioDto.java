package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.RolUsuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CrearUsuarioDto {

    @NotBlank(message = "El correo del usuario es obligatorio")
    @Email(message = "El correo no tiene un formato valido")
    @Size(max = 180, message = "El correo no puede superar 180 caracteres")
    private String correo;

    @NotBlank(message = "La contrasena inicial es obligatoria")
    @Size(min = 8, max = 100, message = "La contrasena debe tener entre 8 y 100 caracteres")
    private String password;

    @NotNull(message = "El rol de acceso es obligatorio")
    private RolUsuario rol;
}

package co.edu.javeriana.procesosempresariales.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "Credenciales para iniciar sesión con JWT.")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class InicioSesionDto {
    @NotBlank(message = "El correo es obligatorio")
    @Size(max = 180, message = "El correo no puede superar 180 caracteres")
    private String correo;

    @NotBlank(message = "La contrasena es obligatoria")
    @Size(max = 100, message = "La contrasena no puede superar 100 caracteres")
    @Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
    private String password;

    @Override
    public String toString() {
        return "InicioSesionDto[correo=" + correo + "]";
    }
}

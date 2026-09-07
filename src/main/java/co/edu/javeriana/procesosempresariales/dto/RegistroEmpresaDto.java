package co.edu.javeriana.procesosempresariales.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class RegistroEmpresaDto {

    @NotBlank(message = "El nombre de la empresa es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
    private String nombre;

    @NotBlank(message = "El NIT es obligatorio")
    @Size(max = 20, message = "El NIT no puede superar 20 caracteres")
    private String nit;

    @NotBlank(message = "El correo de contacto es obligatorio")
    @Email(message = "El correo de contacto no tiene un formato valido")
    @Size(max = 180, message = "El correo no puede superar 180 caracteres")
    private String correoContacto;
}

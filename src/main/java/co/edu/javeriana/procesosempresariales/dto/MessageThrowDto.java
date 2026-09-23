package co.edu.javeriana.procesosempresariales.dto;

import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageThrowDto {

    // El id oculto permite que el mismo formulario sirva para crear y editar.
    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
    private String nombre;

    @NotBlank(message = "El código de referencia es obligatorio")
    @Size(max = 100, message = "El código de referencia no puede superar 100 caracteres")
    private String codigoReferencia;

    @NotBlank(message = "El contenido del mensaje es obligatorio")
    private String contenido;

    @NotBlank(message = "El pool destino es obligatorio")
    @Size(max = 150, message = "El pool destino no puede superar 150 caracteres")
    private String poolDestino;

    // Se usa para informar que todavía no existe un Message Catch de HU-27.
    private List<String> advertencias = new ArrayList<>();
}

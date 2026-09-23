package co.edu.javeriana.procesosempresariales.dto;

import java.util.ArrayList;
import java.util.List;

import co.edu.javeriana.procesosempresariales.domain.VarianteMessageCatch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


//transporta y valida los datos que usa la vista de HU-27
//separa la entrada web del modelo persistente y conserva advertencias de negocio
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MessageCatchDto {

    // Permite reutilizar el formulario para crear y editar.
    private Long id;

    @NotBlank(message = "El nombre del mensaje es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
    private String nombreMensaje;

    @NotNull(message = "La variante del evento es obligatoria")
    private VarianteMessageCatch variante;

    @NotBlank(message = "Los datos esperados son obligatorios")
    private String datosEsperados;

    @NotBlank(message = "Las actividades destino son obligatorias")
    private String actividadesUso;

    private boolean origenExterno;

    // El servicio informa si falta el Message Throw relacionado.
    private List<String> advertencias = new ArrayList<>();
}

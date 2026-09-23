package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.ComportamientoFallo;
import co.edu.javeriana.procesosempresariales.domain.TipoDestinoExterno;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


//transporta solo los datos necesarios entre el formulario y el servicio ademas valida la entrada antes de que llegue a la logica de negocio
 
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EnvioMensajeExternoDto {

    // Se conserva para que el formulario compartido pueda editar un registro existente
    private Long id;

    @NotBlank(message = "El nombre del sistema externo es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
    private String nombreSistemaExterno;

    @NotNull(message = "El tipo de destino es obligatorio")
    private TipoDestinoExterno tipoDestino;

    @NotBlank(message = "Los datos enviados son obligatorios")
    private String datosEnviados;

    @NotBlank(message = "El momento del proceso es obligatorio")
    @Size(max = 150, message = "El momento no puede superar 150 caracteres")
    private String momentoProceso;

    @NotNull(message = "El comportamiento en caso de fallo es obligatorio")
    private ComportamientoFallo comportamientoFallo;
}

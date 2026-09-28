package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.ComportamientoSinCaso;
import co.edu.javeriana.procesosempresariales.domain.VarianteMessageCatch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CrearMessageCatchDto {

    @NotBlank(message = "El nombre del mensaje esperado es obligatorio")
    @Size(max = 150, message = "El nombre del mensaje no puede superar 150 caracteres")
    private String nombreMensaje;

    @NotNull(message = "La variante del Message Catch es obligatoria")
    private VarianteMessageCatch variante;

    @NotBlank(message = "Los datos esperados son obligatorios")
    @Size(max = 2000, message = "Los datos esperados no pueden superar 2000 caracteres")
    private String datosEsperados;

    @NotBlank(message = "Las actividades que usan los datos son obligatorias")
    @Size(max = 1000, message = "Las actividades que usan los datos no pueden superar 1000 caracteres")
    private String actividadesUso;

    private Boolean origenExterno;

    @Size(max = 100, message = "La clave de correlación no puede superar 100 caracteres")
    private String claveCorrelacion;

    private ComportamientoSinCaso comportamientoSinCaso;

    private Long poolId;

    @NotNull(message = "La posición X es obligatoria")
    private Integer posicionX;

    @NotNull(message = "La posición Y es obligatoria")
    private Integer posicionY;

    public boolean esOrigenExterno() {
        return Boolean.TRUE.equals(origenExterno);
    }
}

package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.TipoPool;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CrearPoolDto {

    @NotBlank(message = "El nombre del pool es obligatorio")
    @Size(max = 150, message = "El nombre del pool no puede superar 150 caracteres")
    private String nombre;

    @Schema(description = "PARTICIPANTE (otra empresa registrada) o EXTERNO (participante no registrado). El "
            + "PROPIETARIO se crea con el proceso.")
    @NotNull(message = "El tipo de pool es obligatorio")
    private TipoPool tipo;

    @Schema(description = "Pool sin elementos internos. Los destinos de envíos externos deben serlo.")
    private Boolean cajaNegra;

    @Schema(description = "Empresa registrada que representa un pool PARTICIPANTE; se omite en pools EXTERNO.")
    private Long empresaParticipanteId;

    public boolean esCajaNegra() {
        return Boolean.TRUE.equals(cajaNegra);
    }
}

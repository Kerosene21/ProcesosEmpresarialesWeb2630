package co.edu.javeriana.procesosempresariales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class EditarPoolDto {

    @NotBlank(message = "El nombre del pool es obligatorio")
    @Size(max = 150, message = "El nombre del pool no puede superar 150 caracteres")
    private String nombre;

    private Boolean cajaNegra;

    private Long empresaParticipanteId;

    public boolean esCajaNegra() {
        return Boolean.TRUE.equals(cajaNegra);
    }
}

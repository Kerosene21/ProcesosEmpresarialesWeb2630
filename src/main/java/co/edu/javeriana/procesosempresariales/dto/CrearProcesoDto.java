package co.edu.javeriana.procesosempresariales.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// datos que el cliente puede enviar para crear un proceso
@Getter @Setter @NoArgsConstructor @AllArgsConstructor // Lombok genera acceso y constructores
public class CrearProcesoDto {
    
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 150, message = "El nombre no puede superar 150 caracteres")
    private String nombre; // el servicio lo valida como unico por empresa

    @NotBlank(message = "La descripción es obligatoria")
    private String descripcion; // informacion básica del proceso

    @NotBlank(message = "La categoría es obligatoria")
    @Size(max = 100, message = "La categoría no puede superar 100 caracteres")
    private String categoria; // clasificación para organizar procesos.
}
package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.EstadoProceso;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class FiltroProcesosDto {
    private String q;
    private EstadoProceso estado;
    private String categoria;
    private VisibilidadProceso visibilidad;
    private Integer page;
}

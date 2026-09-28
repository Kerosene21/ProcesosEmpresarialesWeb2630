package co.edu.javeriana.procesosempresariales.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ProcesoUsoRolDto {
    private Long id;
    private String nombre;
    private int lanes;
    private long actividadesActivas;
}

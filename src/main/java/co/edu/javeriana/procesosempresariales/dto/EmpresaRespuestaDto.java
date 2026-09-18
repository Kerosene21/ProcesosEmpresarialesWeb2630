package co.edu.javeriana.procesosempresariales.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class EmpresaRespuestaDto {
    private Long id;
    private String nombre;
    private String nit;
    private String correoContacto;
    private String administradorUsername;
}

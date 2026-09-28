package co.edu.javeriana.procesosempresariales.dto;

import co.edu.javeriana.procesosempresariales.domain.TipoPool;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class PoolRespuestaDto {
    private Long id;
    private Long procesoId;
    private String nombre;
    private TipoPool tipo;
    private int orden;
    private boolean cajaNegra;
    private boolean activo;
    @Schema(description = "Empresa representada: la propietaria en el pool PROPIETARIO, la participante en un "
            + "PARTICIPANTE y null en un EXTERNO.")
    private Long empresaId;
    private String empresaNombre;
}

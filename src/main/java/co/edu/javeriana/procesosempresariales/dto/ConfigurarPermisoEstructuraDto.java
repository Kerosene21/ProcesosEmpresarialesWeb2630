package co.edu.javeriana.procesosempresariales.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ConfigurarPermisoEstructuraDto {

    @NotNull(message = "Indica si el rol puede crear pools")
    private Boolean crearPool;

    @NotNull(message = "Indica si el rol puede editar pools")
    private Boolean editarPool;

    @NotNull(message = "Indica si el rol puede eliminar pools")
    private Boolean eliminarPool;

    @NotNull(message = "Indica si el rol puede crear lanes")
    private Boolean crearLane;

    @NotNull(message = "Indica si el rol puede editar lanes")
    private Boolean editarLane;

    @NotNull(message = "Indica si el rol puede eliminar lanes")
    private Boolean eliminarLane;
}

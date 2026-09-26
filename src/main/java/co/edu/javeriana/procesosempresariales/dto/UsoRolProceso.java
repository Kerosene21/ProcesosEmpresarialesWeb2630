package co.edu.javeriana.procesosempresariales.dto;

public record UsoRolProceso(Long rolProcesoId, Long procesoId, String procesoNombre, Long laneId,
        Long actividadesActivas) {
}

package co.edu.javeriana.procesosempresariales.domain;

public enum OperacionEstructura {
    CREAR_POOL("crear pools"),
    EDITAR_POOL("editar pools"),
    ELIMINAR_POOL("eliminar pools"),
    CREAR_LANE("crear lanes"),
    EDITAR_LANE("editar lanes"),
    ELIMINAR_LANE("eliminar lanes");

    private final String descripcion;

    OperacionEstructura(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}

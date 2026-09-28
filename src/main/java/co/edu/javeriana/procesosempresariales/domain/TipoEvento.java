package co.edu.javeriana.procesosempresariales.domain;

public enum TipoEvento {
    MESSAGE_THROW("Message Throw"),
    MESSAGE_CATCH("Message Catch"),
    ENVIO_EXTERNO("Envío externo");

    private final String descripcion;

    TipoEvento(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}

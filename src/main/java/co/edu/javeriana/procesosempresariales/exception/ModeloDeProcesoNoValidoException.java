package co.edu.javeriana.procesosempresariales.exception;

public class ModeloDeProcesoNoValidoException extends RuntimeException {
    public ModeloDeProcesoNoValidoException(String mensaje) {
        super(mensaje);
    }
}

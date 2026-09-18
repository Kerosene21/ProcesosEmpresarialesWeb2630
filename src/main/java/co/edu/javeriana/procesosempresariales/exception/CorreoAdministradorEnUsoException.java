package co.edu.javeriana.procesosempresariales.exception;

public class CorreoAdministradorEnUsoException extends RuntimeException {
    public CorreoAdministradorEnUsoException(String message) {
        super(message);
    }
}

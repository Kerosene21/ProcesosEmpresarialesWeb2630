package co.edu.javeriana.procesosempresariales.exception;

public class SesionExpiradaException extends RuntimeException {
    public SesionExpiradaException(String message) {
        super(message);
    }
}

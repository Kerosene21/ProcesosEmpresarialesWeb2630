package co.edu.javeriana.procesosempresariales.exception;

public class UsuarioSinPermisoException extends RuntimeException {
    public UsuarioSinPermisoException(String message) {
        super(message);
    }
}
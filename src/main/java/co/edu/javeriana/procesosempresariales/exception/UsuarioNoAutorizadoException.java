package co.edu.javeriana.procesosempresariales.exception;

public class UsuarioNoAutorizadoException extends RuntimeException {
    public UsuarioNoAutorizadoException(String message) {
        super(message);
    }
}
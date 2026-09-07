package co.edu.javeriana.procesosempresariales.exception;

// error cuando se intenta crear un proceso sin identidad autenticada
public class UsuarioNoAutorizadoException extends RuntimeException {
    public UsuarioNoAutorizadoException(String message) {
        super(message);
    }
}
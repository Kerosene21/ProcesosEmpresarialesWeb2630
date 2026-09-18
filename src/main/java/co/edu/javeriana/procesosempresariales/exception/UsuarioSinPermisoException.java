package co.edu.javeriana.procesosempresariales.exception;

// error que indica que el usuario autenticado no tiene permisos para modificar procesos
public class UsuarioSinPermisoException extends RuntimeException {
    public UsuarioSinPermisoException(String message) {
        super(message);
    }
}
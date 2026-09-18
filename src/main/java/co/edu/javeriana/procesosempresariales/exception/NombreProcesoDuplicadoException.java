package co.edu.javeriana.procesosempresariales.exception;

// error cuando el nombre ya existe dentro de la empresa
public class NombreProcesoDuplicadoException extends RuntimeException {
    public NombreProcesoDuplicadoException(String message) {
        super(message);
    }
}
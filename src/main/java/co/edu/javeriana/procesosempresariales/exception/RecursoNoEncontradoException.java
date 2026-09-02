package co.edu.javeriana.procesosempresariales.exception;

// error cuando no existe un recurso solicitado (proceso o diagrama)
public class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException(String message) {
        super(message);
    }
}
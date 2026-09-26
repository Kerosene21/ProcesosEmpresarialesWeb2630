package co.edu.javeriana.procesosempresariales.exception;

import java.util.List;

public class RolProcesoEnUsoException extends RuntimeException {

    private final transient List<String> procesos;

    public RolProcesoEnUsoException(String message, List<String> procesos) {
        super(message);
        this.procesos = List.copyOf(procesos);
    }

    public List<String> getProcesos() {
        return procesos;
    }
}

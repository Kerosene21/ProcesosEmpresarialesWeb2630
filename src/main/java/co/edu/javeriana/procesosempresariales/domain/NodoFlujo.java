package co.edu.javeriana.procesosempresariales.domain;

public record NodoFlujo(TipoNodoFlujo tipo, Long id, String nombre, Integer posicionX, Integer posicionY,
        boolean activo, TipoGateway tipoGateway) {

    public boolean esGateway() {
        return tipo == TipoNodoFlujo.GATEWAY;
    }

    public boolean exigeCondicion() {
        return esGateway() && tipoGateway.exigeCondicion();
    }
}

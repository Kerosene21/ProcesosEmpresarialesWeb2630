package co.edu.javeriana.procesosempresariales.domain;

import java.util.Objects;

public record NodoFlujo(TipoNodoFlujo tipo, Long id, Long poolId, String nombre, Integer posicionX,
        Integer posicionY, boolean activo, TipoGateway tipoGateway) {

    public boolean esGateway() {
        return tipo == TipoNodoFlujo.GATEWAY;
    }

    public boolean exigeCondicion() {
        return esGateway() && tipoGateway.exigeCondicion();
    }

    public boolean mismoPool(NodoFlujo otro) {
        return Objects.equals(poolId, otro.poolId());
    }
}

package co.edu.javeriana.procesosempresariales.domain;

public enum TipoGateway {
    EXCLUSIVO("X"),
    PARALELO("+"),
    INCLUSIVO("O");

    private final String simbolo;

    TipoGateway(String simbolo) {
        this.simbolo = simbolo;
    }

    public String getSimbolo() {
        return simbolo;
    }

    public boolean exigeCondicion() {
        return this != PARALELO;
    }
}

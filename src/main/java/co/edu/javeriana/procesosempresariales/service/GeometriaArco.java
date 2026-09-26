package co.edu.javeriana.procesosempresariales.service;

import org.springframework.stereotype.Component;

import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;

@Component
public class GeometriaArco {

    public static final int ANCHO_ACTIVIDAD = 130;
    public static final int ALTO_ACTIVIDAD = 46;
    public static final int LADO_GATEWAY = 46;

    public PuntoDiagrama centro(TipoNodoFlujo tipo, Integer posicionX, Integer posicionY) {
        int x = posicionX == null ? 0 : posicionX;
        int y = posicionY == null ? 0 : posicionY;
        return new PuntoDiagrama(x + mediaAnchura(tipo), y + mediaAltura(tipo));
    }

    public PuntoDiagrama llegada(PuntoDiagrama origen, PuntoDiagrama destino, TipoNodoFlujo tipoDestino) {
        int deltaX = destino.x() - origen.x();
        int deltaY = destino.y() - origen.y();
        if (deltaX == 0 && deltaY == 0) {
            return destino;
        }
        double escala = Math.min(1.0, Math.min(escalaHasta(mediaAnchura(tipoDestino), deltaX),
                escalaHasta(mediaAltura(tipoDestino), deltaY)));
        return new PuntoDiagrama((int) Math.round(destino.x() - deltaX * escala),
                (int) Math.round(destino.y() - deltaY * escala));
    }

    private double escalaHasta(int margen, int delta) {
        if (delta == 0) {
            return Double.MAX_VALUE;
        }
        return (double) margen / Math.abs(delta);
    }

    private int mediaAnchura(TipoNodoFlujo tipo) {
        return tipo == TipoNodoFlujo.GATEWAY ? LADO_GATEWAY / 2 : ANCHO_ACTIVIDAD / 2;
    }

    private int mediaAltura(TipoNodoFlujo tipo) {
        return tipo == TipoNodoFlujo.GATEWAY ? LADO_GATEWAY / 2 : ALTO_ACTIVIDAD / 2;
    }
}

package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;

class GeometriaArcoTest {

    private final GeometriaArco geometriaArco = new GeometriaArco();

    @Test
    void elCentroDeUnaActividadSeCalculaDesdeSuEsquina() {
        PuntoDiagrama centro = geometriaArco.centro(TipoNodoFlujo.ACTIVIDAD, 100, 50);

        assertThat(centro.x()).isEqualTo(165);
        assertThat(centro.y()).isEqualTo(73);
    }

    @Test
    void elCentroDeUnGatewayUsaElLadoDelRombo() {
        PuntoDiagrama centro = geometriaArco.centro(TipoNodoFlujo.GATEWAY, 300, 100);

        assertThat(centro.x()).isEqualTo(323);
        assertThat(centro.y()).isEqualTo(123);
    }

    @Test
    void unaPosicionSinValorSeTomaComoOrigenDelLienzo() {
        PuntoDiagrama centro = geometriaArco.centro(TipoNodoFlujo.ACTIVIDAD, null, null);

        assertThat(centro.x()).isEqualTo(65);
        assertThat(centro.y()).isEqualTo(23);
    }

    @Test
    void laLlegadaHorizontalSeDetieneEnElBordeIzquierdoDelDestino() {
        PuntoDiagrama origen = new PuntoDiagrama(165, 73);
        PuntoDiagrama destino = new PuntoDiagrama(465, 73);

        PuntoDiagrama llegada = geometriaArco.llegada(origen, destino, TipoNodoFlujo.ACTIVIDAD);

        assertThat(llegada.x()).isEqualTo(400);
        assertThat(llegada.y()).isEqualTo(73);
    }

    @Test
    void laLlegadaVerticalSeDetieneEnElBordeSuperiorDelDestino() {
        PuntoDiagrama origen = new PuntoDiagrama(165, 50);
        PuntoDiagrama destino = new PuntoDiagrama(165, 300);

        PuntoDiagrama llegada = geometriaArco.llegada(origen, destino, TipoNodoFlujo.ACTIVIDAD);

        assertThat(llegada.x()).isEqualTo(165);
        assertThat(llegada.y()).isEqualTo(277);
    }

    @Test
    void laLlegadaAUnGatewayUsaElMargenDelRombo() {
        PuntoDiagrama origen = new PuntoDiagrama(100, 123);
        PuntoDiagrama destino = new PuntoDiagrama(323, 123);

        PuntoDiagrama llegada = geometriaArco.llegada(origen, destino, TipoNodoFlujo.GATEWAY);

        assertThat(llegada.x()).isEqualTo(300);
        assertThat(llegada.y()).isEqualTo(123);
    }

    @Test
    void dosNodosSuperpuestosDejanLaLlegadaEnElCentro() {
        PuntoDiagrama punto = new PuntoDiagrama(165, 73);

        PuntoDiagrama llegada = geometriaArco.llegada(punto, punto, TipoNodoFlujo.ACTIVIDAD);

        assertThat(llegada).isEqualTo(punto);
    }

    @Test
    void unDestinoMuyCercanoNoInvierteLaDireccionDelArco() {
        PuntoDiagrama origen = new PuntoDiagrama(165, 73);
        PuntoDiagrama destino = new PuntoDiagrama(175, 73);

        PuntoDiagrama llegada = geometriaArco.llegada(origen, destino, TipoNodoFlujo.ACTIVIDAD);

        assertThat(llegada).isEqualTo(origen);
    }

    @Test
    void laLlegadaDiagonalUsaElMargenMasRestrictivo() {
        PuntoDiagrama origen = new PuntoDiagrama(0, 0);
        PuntoDiagrama destino = new PuntoDiagrama(200, 200);

        PuntoDiagrama llegada = geometriaArco.llegada(origen, destino, TipoNodoFlujo.ACTIVIDAD);

        assertThat(llegada.x()).isEqualTo(177);
        assertThat(llegada.y()).isEqualTo(177);
    }
}

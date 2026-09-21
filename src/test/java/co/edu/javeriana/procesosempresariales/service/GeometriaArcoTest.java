package co.edu.javeriana.procesosempresariales.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import co.edu.javeriana.procesosempresariales.domain.TipoNodoFlujo;

class GeometriaArcoTest {

    @Test
    void elCentroDeUnaActividadSeCalculaDesdeSuEsquina() {
        GeometriaArco.Punto centro = GeometriaArco.centro(TipoNodoFlujo.ACTIVIDAD, 100, 50);

        assertThat(centro.x()).isEqualTo(165);
        assertThat(centro.y()).isEqualTo(73);
    }

    @Test
    void elCentroDeUnGatewayUsaElLadoDelRombo() {
        GeometriaArco.Punto centro = GeometriaArco.centro(TipoNodoFlujo.GATEWAY, 300, 100);

        assertThat(centro.x()).isEqualTo(323);
        assertThat(centro.y()).isEqualTo(123);
    }

    @Test
    void unaPosicionSinValorSeTomaComoOrigenDelLienzo() {
        GeometriaArco.Punto centro = GeometriaArco.centro(TipoNodoFlujo.ACTIVIDAD, null, null);

        assertThat(centro.x()).isEqualTo(65);
        assertThat(centro.y()).isEqualTo(23);
    }

    @Test
    void laLlegadaHorizontalSeDetieneEnElBordeIzquierdoDelDestino() {
        GeometriaArco.Punto origen = new GeometriaArco.Punto(165, 73);
        GeometriaArco.Punto destino = new GeometriaArco.Punto(465, 73);

        GeometriaArco.Punto llegada = GeometriaArco.llegada(origen, destino, TipoNodoFlujo.ACTIVIDAD);

        assertThat(llegada.x()).isEqualTo(400);
        assertThat(llegada.y()).isEqualTo(73);
    }

    @Test
    void laLlegadaVerticalSeDetieneEnElBordeSuperiorDelDestino() {
        GeometriaArco.Punto origen = new GeometriaArco.Punto(165, 50);
        GeometriaArco.Punto destino = new GeometriaArco.Punto(165, 300);

        GeometriaArco.Punto llegada = GeometriaArco.llegada(origen, destino, TipoNodoFlujo.ACTIVIDAD);

        assertThat(llegada.x()).isEqualTo(165);
        assertThat(llegada.y()).isEqualTo(277);
    }

    @Test
    void laLlegadaAUnGatewayUsaElMargenDelRombo() {
        GeometriaArco.Punto origen = new GeometriaArco.Punto(100, 123);
        GeometriaArco.Punto destino = new GeometriaArco.Punto(323, 123);

        GeometriaArco.Punto llegada = GeometriaArco.llegada(origen, destino, TipoNodoFlujo.GATEWAY);

        assertThat(llegada.x()).isEqualTo(300);
        assertThat(llegada.y()).isEqualTo(123);
    }

    @Test
    void dosNodosSuperpuestosDejanLaLlegadaEnElCentro() {
        GeometriaArco.Punto punto = new GeometriaArco.Punto(165, 73);

        GeometriaArco.Punto llegada = GeometriaArco.llegada(punto, punto, TipoNodoFlujo.ACTIVIDAD);

        assertThat(llegada).isEqualTo(punto);
    }

    @Test
    void unDestinoMuyCercanoNoInvierteLaDireccionDelArco() {
        GeometriaArco.Punto origen = new GeometriaArco.Punto(165, 73);
        GeometriaArco.Punto destino = new GeometriaArco.Punto(175, 73);

        GeometriaArco.Punto llegada = GeometriaArco.llegada(origen, destino, TipoNodoFlujo.ACTIVIDAD);

        assertThat(llegada).isEqualTo(origen);
    }

    @Test
    void laLlegadaDiagonalUsaElMargenMasRestrictivo() {
        GeometriaArco.Punto origen = new GeometriaArco.Punto(0, 0);
        GeometriaArco.Punto destino = new GeometriaArco.Punto(200, 200);

        GeometriaArco.Punto llegada = GeometriaArco.llegada(origen, destino, TipoNodoFlujo.ACTIVIDAD);

        assertThat(llegada.x()).isEqualTo(177);
        assertThat(llegada.y()).isEqualTo(177);
    }
}

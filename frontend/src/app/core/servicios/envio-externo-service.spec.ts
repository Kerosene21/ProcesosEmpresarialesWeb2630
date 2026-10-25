import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CrearEnvioExterno } from '../modelos/evento';
import { FalloHttp } from '../modelos/fallo-http';
import { EnvioExternoService } from './envio-externo-service';

describe('EnvioExternoService', () => {
  let servicio: EnvioExternoService;
  let http: HttpTestingController;
  const ruta = '/api/procesos/5/envios-externos';

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(EnvioExternoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('documenta el envío con su medio, los datos, el momento y el comportamiento ante fallo', () => {
    const datos: CrearEnvioExterno = {
      nombreMensaje: 'NotificarDespacho',
      poolOrigenId: 80,
      poolDestinoId: 82,
      tipoDestino: 'SERVICIO_WEB',
      datosEnviados: 'numeroGuia',
      momentoProceso: 'Al confirmar el despacho',
      comportamientoFallo: 'RUTA_ERROR',
      claveCorrelacion: null,
      posicionX: 820,
      posicionY: 70,
    };

    servicio.crear(5, datos).subscribe();

    const peticion = http.expectOne(ruta);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual(datos);
  });

  it('edita, previsualiza la eliminación y elimina por identificador', () => {
    servicio
      .editar(5, 7, {
        nombreMensaje: 'Notificar',
        poolDestinoId: 82,
        tipoDestino: 'COLA',
        datosEnviados: 'x',
        momentoProceso: 'y',
        comportamientoFallo: 'FINALIZAR',
        claveCorrelacion: 'numeroGuia',
      })
      .subscribe();
    servicio.previsualizarEliminacion(5, 7).subscribe();
    servicio.eliminar(5, 7).subscribe();

    expect(http.expectOne((peticion) => peticion.method === 'PUT').request.url).toBe(`${ruta}/7`);
    expect(http.expectOne(`${ruta}/7/eliminacion`).request.method).toBe('GET');
    expect(http.expectOne((peticion) => peticion.method === 'DELETE').request.url).toBe(`${ruta}/7`);
  });

  it('traduce el rechazo de un destino que no es un sistema externo de caja negra', () => {
    let fallo: FalloHttp | undefined;

    servicio
      .crear(5, {
        nombreMensaje: 'Notificar',
        poolOrigenId: 80,
        poolDestinoId: 81,
        tipoDestino: 'CORREO',
        datosEnviados: 'x',
        momentoProceso: 'y',
        comportamientoFallo: 'CONTINUAR',
        claveCorrelacion: null,
        posicionX: 1,
        posicionY: 1,
      })
      .subscribe({ error: (error: FalloHttp) => (fallo = error) });
    http
      .expectOne(ruta)
      .flush({ codigo: 'ENVIO_EXTERNO_NO_VALIDO', mensaje: "El pool 'Cliente' es de tipo PARTICIPANTE" }, { status: 400, statusText: 'Bad Request' });

    expect(fallo).toMatchObject({ tipo: 'datos-no-validos', codigo: 'ENVIO_EXTERNO_NO_VALIDO' });
  });
});

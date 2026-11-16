import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CrearMessageCatch } from '../modelos/evento';
import { FalloHttp } from '../modelos/fallo-http';
import { MessageCatchService } from './message-catch-service';

describe('MessageCatchService', () => {
  let servicio: MessageCatchService;
  let http: HttpTestingController;
  const ruta = '/api/procesos/5/message-catches';

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(MessageCatchService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('crea un Message Catch de inicio con su pool, los datos esperados y el origen', () => {
    const datos: CrearMessageCatch = {
      nombreMensaje: 'Solicitud',
      variante: 'INICIO',
      datosEsperados: 'numeroSolicitud',
      actividadesUso: 'Registrar solicitud',
      origenExterno: true,
      claveCorrelacion: null,
      comportamientoSinCaso: null,
      poolId: 80,
      posicionX: 60,
      posicionY: 70,
    };

    servicio.crear(5, datos).subscribe();

    const peticion = http.expectOne(ruta);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual(datos);
  });

  it('edita, previsualiza la eliminación y elimina por identificador', () => {
    servicio
      .editar(5, 5, {
        nombreMensaje: 'Solicitud',
        variante: 'INTERMEDIO',
        datosEsperados: 'x',
        actividadesUso: 'y',
        origenExterno: false,
        claveCorrelacion: 'numeroSolicitud',
        comportamientoSinCaso: 'DESCARTAR',
      })
      .subscribe();
    servicio.previsualizarEliminacion(5, 5).subscribe();
    servicio.eliminar(5, 5).subscribe();

    expect(http.expectOne((peticion) => peticion.method === 'PUT').request.url).toBe(`${ruta}/5`);
    expect(http.expectOne(`${ruta}/5/eliminacion`).request.method).toBe('GET');
    expect(http.expectOne((peticion) => peticion.method === 'DELETE').request.url).toBe(`${ruta}/5`);
  });

  it('traduce el rechazo de un Message Catch de inicio con entradas', () => {
    let fallo: FalloHttp | undefined;

    servicio
      .editar(5, 5, {
        nombreMensaje: 'Solicitud',
        variante: 'INICIO',
        datosEsperados: 'x',
        actividadesUso: 'y',
        origenExterno: false,
        claveCorrelacion: null,
        comportamientoSinCaso: null,
      })
      .subscribe({ error: (error: FalloHttp) => (fallo = error) });
    http
      .expectOne(`${ruta}/5`)
      .flush({ codigo: 'CATCH_INICIO_CON_ENTRADA', mensaje: 'Tiene 1 flujos de secuencia entrantes' }, { status: 400, statusText: 'Bad Request' });

    expect(fallo).toMatchObject({ tipo: 'datos-no-validos', codigo: 'CATCH_INICIO_CON_ENTRADA' });
  });

  it('mueve el evento con su propio endpoint de posición', () => {
    servicio.mover(5, 5, { posicionX: 120, posicionY: 90 }).subscribe();

    const peticion = http.expectOne(`${ruta}/5/posicion`);
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ posicionX: 120, posicionY: 90 });
  });
});

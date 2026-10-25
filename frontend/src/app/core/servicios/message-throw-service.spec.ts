import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CrearMessageThrow } from '../modelos/evento';
import { FalloHttp } from '../modelos/fallo-http';
import { MessageThrowService } from './message-throw-service';

describe('MessageThrowService', () => {
  let servicio: MessageThrowService;
  let http: HttpTestingController;
  const ruta = '/api/procesos/5/message-throws';

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(MessageThrowService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('crea el evento con los pools, el contenido, la correlación y la posición', () => {
    const datos: CrearMessageThrow = {
      nombreMensaje: 'FacturaAprobada',
      contenido: 'numeroFactura, valorTotal',
      poolOrigenId: 80,
      poolDestinoId: 81,
      claveCorrelacion: 'numeroFactura',
      comportamientoSinCaso: 'DESCARTAR',
      posicionX: 200,
      posicionY: 60,
    };

    servicio.crear(5, datos).subscribe();

    const peticion = http.expectOne(ruta);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual(datos);
  });

  it('edita, previsualiza la eliminación y elimina por identificador', () => {
    servicio
      .editar(5, 6, { nombreMensaje: 'Factura', contenido: 'x', poolDestinoId: 82, claveCorrelacion: null, comportamientoSinCaso: null })
      .subscribe();
    servicio.previsualizarEliminacion(5, 6).subscribe();
    servicio.eliminar(5, 6).subscribe();

    expect(http.expectOne((peticion) => peticion.method === 'PUT').request.url).toBe(`${ruta}/6`);
    expect(http.expectOne(`${ruta}/6/eliminacion`).request.method).toBe('GET');
    expect(http.expectOne((peticion) => peticion.method === 'DELETE').request.url).toBe(`${ruta}/6`);
  });

  it('traduce el rechazo de un mensaje dirigido al mismo pool', () => {
    let fallo: FalloHttp | undefined;

    servicio
      .editar(5, 6, { nombreMensaje: 'Factura', contenido: 'x', poolDestinoId: 80, claveCorrelacion: null, comportamientoSinCaso: null })
      .subscribe({ error: (error: FalloHttp) => (fallo = error) });
    http
      .expectOne(`${ruta}/6`)
      .flush({ codigo: 'MENSAJE_ENTRE_MISMO_POOL', mensaje: 'El pool destino coincide con el origen' }, { status: 400, statusText: 'Bad Request' });

    expect(fallo).toMatchObject({ tipo: 'datos-no-validos', codigo: 'MENSAJE_ENTRE_MISMO_POOL' });
  });

  it('mueve el evento con su propio endpoint de posición', () => {
    servicio.mover(5, 6, { posicionX: 120, posicionY: 90 }).subscribe();

    const peticion = http.expectOne(`${ruta}/6/posicion`);
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ posicionX: 120, posicionY: 90 });
  });
});

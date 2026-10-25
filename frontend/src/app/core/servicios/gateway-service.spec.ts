import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { FalloHttp } from '../modelos/fallo-http';
import { GatewayService } from './gateway-service';

describe('GatewayService', () => {
  let servicio: GatewayService;
  let http: HttpTestingController;
  const ruta = '/api/procesos/5/gateways';

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(GatewayService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('crea el gateway con tipo, posición y pool', () => {
    servicio.crear(5, { tipo: 'PARALELO', posicionX: 300, posicionY: 80, poolId: 80 }).subscribe();

    const peticion = http.expectOne(ruta);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({ tipo: 'PARALELO', posicionX: 300, posicionY: 80, poolId: 80 });
  });

  it('edita el tipo y las condiciones indexadas por el arco saliente', () => {
    servicio.editar(5, 4, { tipo: 'INCLUSIVO', condiciones: { 34: 'Aprobada', 35: 'Rechazada' } }).subscribe();

    const peticion = http.expectOne(`${ruta}/4`);
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ tipo: 'INCLUSIVO', condiciones: { 34: 'Aprobada', 35: 'Rechazada' } });
  });

  it('consulta la vista previa de eliminación antes de eliminar', () => {
    servicio.previsualizarEliminacion(5, 4).subscribe();
    servicio.eliminar(5, 4).subscribe();

    expect(http.expectOne(`${ruta}/4/eliminacion`).request.method).toBe('GET');
    expect(http.expectOne(`${ruta}/4`).request.method).toBe('DELETE');
  });

  it('traduce la condición omitida y el pool de caja negra', () => {
    const fallos: FalloHttp[] = [];
    const guardar = { error: (fallo: FalloHttp) => fallos.push(fallo) };

    servicio.editar(5, 4, { tipo: 'EXCLUSIVO', condiciones: {} }).subscribe(guardar);
    http
      .expectOne(`${ruta}/4`)
      .flush({ codigo: 'CONDICION_NO_VALIDA', mensaje: 'Cada arco de salida de un gateway exclusivo o inclusivo necesita condición' }, { status: 400, statusText: 'Bad Request' });
    servicio.crear(5, { tipo: 'EXCLUSIVO', posicionX: 0, posicionY: 0, poolId: 82 }).subscribe(guardar);
    http.expectOne(ruta).flush({ codigo: 'POOL_CAJA_NEGRA', mensaje: 'El pool es una caja negra' }, { status: 409, statusText: 'Conflict' });

    expect(fallos).toMatchObject([
      { tipo: 'datos-no-validos', codigo: 'CONDICION_NO_VALIDA' },
      { tipo: 'conflicto', codigo: 'POOL_CAJA_NEGRA' },
    ]);
  });
});

import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { FalloHttp } from '../modelos/fallo-http';
import { PoolService } from './pool-service';

describe('PoolService', () => {
  let servicio: PoolService;
  let http: HttpTestingController;
  const ruta = '/api/procesos/5/pools';

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(PoolService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('consulta los pools activos del proceso', () => {
    servicio.listar(5).subscribe();

    expect(http.expectOne(ruta).request.method).toBe('GET');
  });

  it('crea un pool con su tipo, la caja negra y la empresa participante', () => {
    const datos = { nombre: 'Proveedor', tipo: 'PARTICIPANTE' as const, cajaNegra: false, empresaParticipanteId: 9 };

    servicio.crear(5, datos).subscribe();

    const peticion = http.expectOne(ruta);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual(datos);
  });

  it('edita y elimina un pool por identificador', () => {
    servicio.editar(5, 81, { nombre: 'Banco', cajaNegra: true, empresaParticipanteId: null }).subscribe();
    servicio.eliminar(5, 81).subscribe();

    const edicion = http.expectOne((peticion) => peticion.method === 'PUT');
    expect(edicion.request.url).toBe(`${ruta}/81`);
    expect(edicion.request.body).toEqual({ nombre: 'Banco', cajaNegra: true, empresaParticipanteId: null });
    expect(http.expectOne((peticion) => peticion.method === 'DELETE').request.url).toBe(`${ruta}/81`);
  });

  it('traduce el rechazo de eliminar un pool con contenido', () => {
    let fallo: FalloHttp | undefined;

    servicio.eliminar(5, 81).subscribe({ error: (error: FalloHttp) => (fallo = error) });
    http
      .expectOne(`${ruta}/81`)
      .flush(
        { codigo: 'POOL_CON_CONTENIDO', mensaje: "El pool 'Banco' no se puede eliminar: contiene 1 eventos activos" },
        { status: 409, statusText: 'Conflict' },
      );

    expect(fallo).toMatchObject({ tipo: 'conflicto', codigo: 'POOL_CON_CONTENIDO' });
  });
});

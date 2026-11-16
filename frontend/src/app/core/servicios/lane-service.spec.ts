import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { FalloHttp } from '../modelos/fallo-http';
import { LaneService } from './lane-service';

describe('LaneService', () => {
  let servicio: LaneService;
  let http: HttpTestingController;
  const ruta = '/api/procesos/5/pools/80/lanes';

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(LaneService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('consulta las lanes del pool y los roles disponibles para ellas', () => {
    servicio.listar(5, 80).subscribe();
    servicio.rolesDisponibles(5, 80).subscribe();

    expect(http.expectOne(ruta).request.method).toBe('GET');
    expect(http.expectOne('/api/procesos/5/pools/80/roles-disponibles').request.method).toBe('GET');
  });

  it('crea y edita lanes con el rol de proceso y la posición opcional', () => {
    servicio.crear(5, 80, { rolProcesoId: 21, orden: null }).subscribe();
    servicio.editar(5, 80, 12, { rolProcesoId: 22, orden: null }).subscribe();

    const creacion = http.expectOne((peticion) => peticion.method === 'POST');
    expect(creacion.request.url).toBe(ruta);
    expect(creacion.request.body).toEqual({ rolProcesoId: 21, orden: null });
    const edicion = http.expectOne((peticion) => peticion.method === 'PUT');
    expect(edicion.request.url).toBe(`${ruta}/12`);
    expect(edicion.request.body).toEqual({ rolProcesoId: 22, orden: null });
  });

  it('reordena enviando todas las lanes y elimina por identificador', () => {
    servicio.reordenar(5, 80, { lanes: [12, 11] }).subscribe();
    servicio.eliminar(5, 80, 12).subscribe();

    const orden = http.expectOne(`${ruta}/orden`);
    expect(orden.request.method).toBe('PUT');
    expect(orden.request.body).toEqual({ lanes: [12, 11] });
    expect(http.expectOne(`${ruta}/12`).request.method).toBe('DELETE');
  });

  it('traduce el rechazo de eliminar una lane con actividades', () => {
    let fallo: FalloHttp | undefined;

    servicio.eliminar(5, 80, 12).subscribe({ error: (error: FalloHttp) => (fallo = error) });
    http
      .expectOne(`${ruta}/12`)
      .flush({ codigo: 'LANE_CON_ACTIVIDADES', mensaje: 'La lane tiene 1 actividades activas' }, { status: 409, statusText: 'Conflict' });

    expect(fallo).toMatchObject({ tipo: 'conflicto', codigo: 'LANE_CON_ACTIVIDADES' });
  });
});

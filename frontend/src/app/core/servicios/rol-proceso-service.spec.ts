import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { FalloHttp } from '../modelos/fallo-http';
import { RolProcesoService } from './rol-proceso-service';

describe('RolProcesoService', () => {
  let servicio: RolProcesoService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(RolProcesoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista roles con el filtro, la visibilidad y la página del contrato', () => {
    servicio.listar({ q: '  super ', visibilidad: 'TODOS', page: 1 }).subscribe();

    const solicitud = http.expectOne((peticion) => peticion.url === '/api/roles-proceso');
    expect(solicitud.request.method).toBe('GET');
    expect(solicitud.request.params.get('q')).toBe('super');
    expect(solicitud.request.params.get('visibilidad')).toBe('TODOS');
    expect(solicitud.request.params.get('page')).toBe('1');
    solicitud.flush({ content: [], page: { size: 10, number: 1, totalElements: 11, totalPages: 2 } });
  });

  it('consulta el detalle, el historial y la vista previa de eliminación', () => {
    servicio.obtener(4).subscribe();
    servicio.historial(4).subscribe();
    servicio.previsualizarEliminacion(4).subscribe();

    expect(http.expectOne('/api/roles-proceso/4').request.method).toBe('GET');
    expect(http.expectOne('/api/roles-proceso/4/historial').request.method).toBe('GET');
    expect(http.expectOne('/api/roles-proceso/4/eliminacion').request.method).toBe('GET');
  });

  it('crea, edita y elimina con los verbos y cuerpos de los DTO', () => {
    servicio.crear({ nombre: 'Supervisor', descripcion: 'Aprueba solicitudes' }).subscribe();
    servicio.editar(4, { nombre: 'Supervisor', descripcion: 'Aprueba y archiva' }).subscribe();
    servicio.eliminar(4).subscribe();

    const creacion = http.expectOne((peticion) => peticion.method === 'POST');
    expect(creacion.request.url).toBe('/api/roles-proceso');
    expect(creacion.request.body).toEqual({ nombre: 'Supervisor', descripcion: 'Aprueba solicitudes' });
    const edicion = http.expectOne((peticion) => peticion.method === 'PUT');
    expect(edicion.request.url).toBe('/api/roles-proceso/4');
    expect(http.expectOne((peticion) => peticion.method === 'DELETE').request.url).toBe('/api/roles-proceso/4');
  });

  it('traduce el conflicto de un rol en uso', () => {
    let fallo: FalloHttp | undefined;

    servicio.eliminar(4).subscribe({ error: (error: FalloHttp) => (fallo = error) });
    http
      .expectOne('/api/roles-proceso/4')
      .flush(
        { codigo: 'ROL_PROCESO_EN_USO', mensaje: 'El rol lo usan lanes de: Compras', procesos: ['Compras'] },
        { status: 409, statusText: 'Conflict' },
      );

    expect(fallo).toMatchObject({ tipo: 'conflicto', codigo: 'ROL_PROCESO_EN_USO', detalle: 'El rol lo usan lanes de: Compras' });
  });
});

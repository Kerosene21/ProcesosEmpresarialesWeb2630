import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { FalloHttp } from '../modelos/fallo-http';
import { ActividadService } from './actividad-service';

describe('ActividadService', () => {
  let servicio: ActividadService;
  let http: HttpTestingController;
  const ruta = '/api/procesos/5/actividades';

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(ActividadService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('crea la actividad con nombre, tipo, lane y posición inicial', () => {
    const datos = { nombre: 'Revisar', tipo: 'TAREA_USUARIO' as const, laneId: 11, posicionX: 200, posicionY: 60 };

    servicio.crear(5, datos).subscribe();

    const peticion = http.expectOne(ruta);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual(datos);
  });

  it('edita nombre, tipo y lane sin enviar coordenadas', () => {
    servicio.editar(5, 3, { nombre: 'Aprobar', tipo: 'TAREA_MANUAL', laneId: 12 }).subscribe();

    const peticion = http.expectOne(`${ruta}/3`);
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ nombre: 'Aprobar', tipo: 'TAREA_MANUAL', laneId: 12 });
  });

  it('elimina por identificador', () => {
    servicio.eliminar(5, 3).subscribe();

    expect(http.expectOne(`${ruta}/3`).request.method).toBe('DELETE');
  });

  it('traduce el nombre duplicado y la falta de permisos', () => {
    const fallos: FalloHttp[] = [];
    const guardar = { error: (fallo: FalloHttp) => fallos.push(fallo) };

    servicio.crear(5, { nombre: 'Revisar', tipo: 'TAREA_USUARIO', laneId: 11, posicionX: 0, posicionY: 0 }).subscribe(guardar);
    http
      .expectOne(ruta)
      .flush({ codigo: 'ACTIVIDAD_NOMBRE_DUPLICADO', mensaje: 'Ya existe una actividad con ese nombre en el proceso' }, { status: 409, statusText: 'Conflict' });
    servicio.eliminar(5, 3).subscribe(guardar);
    http
      .expectOne(`${ruta}/3`)
      .flush({ codigo: 'USUARIO_SIN_PERMISO', mensaje: 'Solo un administrador puede eliminar actividades' }, { status: 403, statusText: 'Forbidden' });

    expect(fallos).toMatchObject([
      { tipo: 'conflicto', codigo: 'ACTIVIDAD_NOMBRE_DUPLICADO' },
      { tipo: 'sin-permiso', detalle: 'Solo un administrador puede eliminar actividades' },
    ]);
  });

  it('mueve la actividad enviando juntas la lane y la posición', () => {
    servicio.mover(5, 2, { laneId: 12, posicionX: 340, posicionY: 200 }).subscribe();

    const peticion = http.expectOne(`${ruta}/2/posicion`);
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ laneId: 12, posicionX: 340, posicionY: 200 });
  });

  it('traduce el rechazo de pasar a una lane de otro pool con arcos', () => {
    let fallo: FalloHttp | undefined;

    servicio.mover(5, 2, { laneId: 40, posicionX: 340, posicionY: 600 }).subscribe({ error: (actual) => (fallo = actual) });
    http
      .expectOne(`${ruta}/2/posicion`)
      .flush({ codigo: 'SECUENCIA_ENTRE_POOLS', mensaje: "'Evaluar solicitud' tiene 2 arcos conectados" }, { status: 400, statusText: 'Bad Request' });

    expect(fallo).toMatchObject({ tipo: 'datos-no-validos', codigo: 'SECUENCIA_ENTRE_POOLS' });
  });
});

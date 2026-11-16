import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { CrearArco } from '../modelos/arco';
import { FalloHttp } from '../modelos/fallo-http';
import { ArcoService } from './arco-service';

describe('ArcoService', () => {
  let servicio: ArcoService;
  let http: HttpTestingController;
  const ruta = '/api/procesos/5/arcos';
  const datos: CrearArco = {
    origenTipo: 'GATEWAY',
    origenId: 4,
    destinoTipo: 'ACTIVIDAD',
    destinoId: 3,
    etiqueta: 'Sí',
    condicion: 'Aprobada',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(ArcoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('crea el arco con los tipos e identificadores de sus extremos', () => {
    servicio.crear(5, datos).subscribe();

    const peticion = http.expectOne(ruta);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual(datos);
  });

  it('edita extremos, etiqueta y condición', () => {
    servicio.editar(5, 34, { ...datos, destinoId: 2, etiqueta: null }).subscribe();

    const peticion = http.expectOne(`${ruta}/34`);
    expect(peticion.request.method).toBe('PUT');
    expect(peticion.request.body).toEqual({ ...datos, destinoId: 2, etiqueta: null });
  });

  it('elimina por identificador', () => {
    servicio.eliminar(5, 34).subscribe();

    expect(http.expectOne(`${ruta}/34`).request.method).toBe('DELETE');
  });

  it('traduce las reglas BPMN que rechaza el backend', () => {
    const fallos: FalloHttp[] = [];
    const guardar = { error: (fallo: FalloHttp) => fallos.push(fallo) };

    servicio.crear(5, datos).subscribe(guardar);
    http
      .expectOne(ruta)
      .flush({ codigo: 'ARCO_DUPLICADO', mensaje: 'Ya existe un arco activo entre ese origen y ese destino' }, { status: 409, statusText: 'Conflict' });
    servicio.crear(5, datos).subscribe(guardar);
    http
      .expectOne(ruta)
      .flush({ codigo: 'SECUENCIA_ENTRE_POOLS', mensaje: 'Un flujo de secuencia no cruza pools' }, { status: 400, statusText: 'Bad Request' });

    expect(fallos).toMatchObject([
      { tipo: 'conflicto', codigo: 'ARCO_DUPLICADO' },
      { tipo: 'datos-no-validos', codigo: 'SECUENCIA_ENTRE_POOLS', detalle: 'Un flujo de secuencia no cruza pools' },
    ]);
  });
});

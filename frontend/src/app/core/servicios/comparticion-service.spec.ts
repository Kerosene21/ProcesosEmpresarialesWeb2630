import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { FalloHttp } from '../modelos/fallo-http';
import { ComparticionService } from './comparticion-service';

describe('ComparticionService', () => {
  let servicio: ComparticionService;
  let http: HttpTestingController;
  const ruta = '/api/procesos/5/compartido-con';

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(ComparticionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('consulta las empresas con acceso de solo lectura al proceso', () => {
    let empresas: unknown;

    servicio.listar(5).subscribe((respuesta) => (empresas = respuesta));
    const peticion = http.expectOne(ruta);
    peticion.flush([{ id: 9, nombre: 'Andes SAS' }]);

    expect(peticion.request.method).toBe('GET');
    expect(empresas).toEqual([{ id: 9, nombre: 'Andes SAS' }]);
  });

  it('comparte con una empresa por su identificador y sin cuerpo', () => {
    servicio.compartir(5, 9).subscribe();

    const peticion = http.expectOne(`${ruta}/9`);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toBeNull();
  });

  it('retira el acceso de una empresa', () => {
    servicio.dejarDeCompartir(5, 9).subscribe();

    expect(http.expectOne(`${ruta}/9`).request.method).toBe('DELETE');
  });

  it('traduce el rechazo de compartir dos veces con la misma empresa', () => {
    let fallo: FalloHttp | undefined;

    servicio.compartir(5, 9).subscribe({ error: (error: FalloHttp) => (fallo = error) });
    http
      .expectOne(`${ruta}/9`)
      .flush({ codigo: 'PROCESO_YA_COMPARTIDO', mensaje: 'El proceso ya está compartido con esa empresa' }, { status: 409, statusText: 'Conflict' });

    expect(fallo).toMatchObject({ tipo: 'conflicto', codigo: 'PROCESO_YA_COMPARTIDO', detalle: 'El proceso ya está compartido con esa empresa' });
  });
});

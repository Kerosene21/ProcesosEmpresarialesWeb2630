import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import type { MockInstance } from 'vitest';

import { SesionService } from '../servicios/sesion-service';
import { sesionExpiradaInterceptor } from './sesion-expirada-interceptor';

describe('sesionExpiradaInterceptor', () => {
  let http: HttpTestingController;
  let cliente: HttpClient;
  let navegar: MockInstance;
  const autenticado = signal(true);
  const olvidar = vi.fn(() => autenticado.set(false));

  beforeEach(() => {
    autenticado.set(true);
    olvidar.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([sesionExpiradaInterceptor])),
        provideHttpClientTesting(),
        { provide: SesionService, useValue: { autenticado, olvidar } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    cliente = TestBed.inject(HttpClient);
    navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => http.verify());

  function fallar(url: string, estado: number): HttpErrorResponse | undefined {
    let error: HttpErrorResponse | undefined;
    cliente.get(url).subscribe({ error: (recibido: HttpErrorResponse) => (error = recibido) });
    http.expectOne(url).flush({ codigo: 'X', mensaje: 'x' }, { status: estado, statusText: 'Error' });
    return error;
  }

  it('ante un 401 de la API olvida la sesión, lleva al inicio de sesión y propaga el error', () => {
    const error = fallar('/api/procesos/5', 401);

    expect(olvidar).toHaveBeenCalledTimes(1);
    expect(navegar).toHaveBeenCalledWith(['/iniciar-sesion'], { queryParams: { retorno: '/', motivo: 'expirada' } });
    expect(error?.status).toBe(401);
  });

  it('no redirige por los 401 del propio inicio de sesión para evitar bucles', () => {
    const error = fallar('/api/sesion', 401);

    expect(olvidar).not.toHaveBeenCalled();
    expect(navegar).not.toHaveBeenCalled();
    expect(error?.status).toBe(401);
  });

  it('solo redirige una vez aunque lleguen varios 401 seguidos', () => {
    fallar('/api/procesos', 401);
    fallar('/api/procesos/5/diagrama', 401);

    expect(olvidar).toHaveBeenCalledTimes(1);
    expect(navegar).toHaveBeenCalledTimes(1);
  });

  it('no interviene en otros errores ni oculta la respuesta', () => {
    const prohibido = fallar('/api/procesos/5', 403);
    const conflicto = fallar('/api/procesos', 409);

    expect(prohibido?.status).toBe(403);
    expect(conflicto?.status).toBe(409);
    expect(olvidar).not.toHaveBeenCalled();
    expect(navegar).not.toHaveBeenCalled();
  });
});

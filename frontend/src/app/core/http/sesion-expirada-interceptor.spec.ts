import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
  withXsrfConfiguration,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import type { MockInstance } from 'vitest';

import { RespuestaToken, Sesion } from '../modelos/sesion';
import { SesionService } from '../servicios/sesion-service';
import { autorizacionInterceptor } from './autorizacion-interceptor';
import { sesionExpiradaInterceptor } from './sesion-expirada-interceptor';

const USUARIO: Sesion = {
  autenticado: true,
  correo: 'editor@alpes.com',
  rol: 'EDITOR',
  empresaId: 10,
  empresaNombre: 'Alpes Logistica',
};

const NO_AUTORIZADO = { status: 401, statusText: 'Unauthorized' };
const SIN_CONTENIDO = { status: 204, statusText: 'No Content' };

function respuesta(token: string): RespuestaToken {
  return { tokenAcceso: token, tipoToken: 'Bearer', expiraEnSegundos: 900, usuario: USUARIO };
}

describe('sesionExpiradaInterceptor', () => {
  let http: HttpTestingController;
  let cliente: HttpClient;
  let sesion: SesionService;
  let navegar: MockInstance;

  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-csrf; path=/';
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(
          withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
          withInterceptors([sesionExpiradaInterceptor, autorizacionInterceptor]),
        ),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    cliente = TestBed.inject(HttpClient);
    sesion = TestBed.inject(SesionService);
    navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    http.verify();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  function iniciarSesion(token = 'jwt-1'): void {
    sesion.iniciar({ correo: USUARIO.correo ?? '', password: 'Clave-2026' }).subscribe();
    http.expectOne('/api/auth/login').flush(respuesta(token));
  }

  function rechazar(url: string, estado = 401): void {
    const pendiente = http.expectOne((peticion) => peticion.url === url);
    pendiente.flush({ codigo: 'X', mensaje: 'x' }, { status: estado, statusText: 'Error' });
  }

  it('ante un 401 renueva una sola vez y reintenta la petición con el token nuevo', () => {
    iniciarSesion('jwt-1');
    let recibido: unknown;

    cliente.get('/api/procesos').subscribe((cuerpo) => (recibido = cuerpo));
    const original = http.expectOne('/api/procesos');
    expect(original.request.headers.get('Authorization')).toBe('Bearer jwt-1');
    original.flush({ codigo: 'USUARIO_NO_AUTORIZADO', mensaje: 'x' }, NO_AUTORIZADO);
    const refresco = http.expectOne('/api/auth/refresh');
    expect(refresco.request.headers.has('Authorization')).toBe(false);
    refresco.flush(respuesta('jwt-2'));
    const reintento = http.expectOne('/api/procesos');
    expect(reintento.request.headers.get('Authorization')).toBe('Bearer jwt-2');
    reintento.flush({ content: [] });

    expect(recibido).toEqual({ content: [] });
    expect(navegar).not.toHaveBeenCalled();
  });

  it('varios 401 simultáneos comparten un único refresh y cada petición se reintenta una vez', () => {
    iniciarSesion('jwt-1');
    const recibidos: unknown[] = [];

    cliente.get('/api/procesos').subscribe((cuerpo) => recibidos.push(cuerpo));
    cliente.get('/api/roles-proceso').subscribe((cuerpo) => recibidos.push(cuerpo));
    cliente.get('/api/auth/me').subscribe((cuerpo) => recibidos.push(cuerpo));
    rechazar('/api/procesos');
    rechazar('/api/roles-proceso');
    rechazar('/api/auth/me');
    http.expectOne('/api/auth/refresh').flush(respuesta('jwt-2'));
    for (const url of ['/api/procesos', '/api/roles-proceso', '/api/auth/me']) {
      const reintento = http.expectOne(url);
      expect(reintento.request.headers.get('Authorization')).toBe('Bearer jwt-2');
      reintento.flush({ url });
    }

    expect(recibidos).toHaveLength(3);
    http.expectNone('/api/auth/refresh');
  });

  it('si el reintento vuelve a recibir 401 no renueva de nuevo y expira la sesión una sola vez', () => {
    iniciarSesion();
    let error: HttpErrorResponse | undefined;

    cliente.get('/api/procesos/5').subscribe({ error: (recibido) => (error = recibido) });
    rechazar('/api/procesos/5');
    http.expectOne('/api/auth/refresh').flush(respuesta('jwt-2'));
    rechazar('/api/procesos/5');

    http.expectNone('/api/auth/refresh');
    expect(error?.status).toBe(401);
    expect(sesion.autenticado()).toBe(false);
    expect(navegar).toHaveBeenCalledTimes(1);
    expect(navegar).toHaveBeenCalledWith(['/iniciar-sesion'], {
      queryParams: { retorno: '/', motivo: 'expirada' },
    });
  });

  it('si el refresh falla no reintenta, expira la sesión y propaga el 401 original', () => {
    iniciarSesion();
    let error: HttpErrorResponse | undefined;

    cliente.get('/api/procesos').subscribe({ error: (recibido) => (error = recibido) });
    cliente.get('/api/procesos/5/diagrama').subscribe({ error: () => undefined });
    rechazar('/api/procesos');
    rechazar('/api/procesos/5/diagrama');
    http
      .expectOne('/api/auth/refresh')
      .flush({ codigo: 'SESION_EXPIRADA', mensaje: 'x' }, NO_AUTORIZADO);

    http.expectNone('/api/procesos');
    http.expectNone('/api/auth/refresh');
    expect(error?.status).toBe(401);
    expect(sesion.autenticado()).toBe(false);
    expect(sesion.tokenAcceso()).toBeNull();
    expect(navegar).toHaveBeenCalledTimes(1);
  });

  it('un fallo de red durante el refresh no cierra la sesión', () => {
    iniciarSesion();
    let error: HttpErrorResponse | undefined;

    cliente.get('/api/procesos').subscribe({ error: (recibido) => (error = recibido) });
    rechazar('/api/procesos');
    http
      .expectOne('/api/auth/refresh')
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

    expect(error?.status).toBe(401);
    expect(sesion.autenticado()).toBe(true);
    expect(navegar).not.toHaveBeenCalled();
  });

  it('un 403 no provoca refresh ni cierra la sesión', () => {
    iniciarSesion();
    let error: HttpErrorResponse | undefined;

    cliente.post('/api/procesos', {}).subscribe({ error: (recibido) => (error = recibido) });
    rechazar('/api/procesos', 403);

    http.expectNone('/api/auth/refresh');
    expect(error?.status).toBe(403);
    expect(sesion.autenticado()).toBe(true);
    expect(navegar).not.toHaveBeenCalled();
  });

  it('un 401 del login es un error de credenciales y no una sesión expirada', () => {
    iniciarSesion();
    let error: HttpErrorResponse | undefined;

    cliente.post('/api/auth/login', {}).subscribe({ error: (recibido) => (error = recibido) });
    rechazar('/api/auth/login');

    http.expectNone('/api/auth/refresh');
    expect(error?.status).toBe(401);
    expect(sesion.autenticado()).toBe(true);
    expect(navegar).not.toHaveBeenCalled();
  });

  it('un 401 del propio refresh, del logout o de la sesión anterior nunca provoca otro refresh', () => {
    iniciarSesion();

    for (const url of ['/api/auth/refresh', '/api/auth/logout', '/api/sesion']) {
      cliente.post(url, null).subscribe({ error: () => undefined });
      rechazar(url);
    }

    http.expectNone('/api/auth/refresh');
    expect(navegar).not.toHaveBeenCalled();
  });

  it('sin una sesión autenticada un 401 no intenta renovar', () => {
    let error: HttpErrorResponse | undefined;

    cliente.get('/api/procesos').subscribe({ error: (recibido) => (error = recibido) });
    const peticion = http.expectOne('/api/procesos');
    expect(peticion.request.headers.has('Authorization')).toBe(false);
    peticion.flush({ codigo: 'USUARIO_NO_AUTORIZADO', mensaje: 'x' }, NO_AUTORIZADO);

    http.expectNone('/api/auth/refresh');
    expect(error?.status).toBe(401);
    expect(navegar).not.toHaveBeenCalled();
  });

  it('cerrar sesión durante un refresh impide el reintento y la restauración de la sesión', () => {
    iniciarSesion('jwt-1');
    let error: HttpErrorResponse | undefined;

    cliente.get('/api/procesos').subscribe({ error: (recibido) => (error = recibido) });
    rechazar('/api/procesos');
    const refresco = http.expectOne('/api/auth/refresh');
    sesion.cerrar().subscribe();
    http.expectOne('/api/auth/logout').flush(null, SIN_CONTENIDO);
    http.expectOne('/api/sesion').flush(null, SIN_CONTENIDO);
    refresco.flush(respuesta('jwt-tardio'));

    http.expectNone('/api/procesos');
    expect(error?.status).toBe(401);
    expect(sesion.autenticado()).toBe(false);
    expect(sesion.tokenAcceso()).toBeNull();
    expect(navegar).not.toHaveBeenCalled();
  });

  it('navegar mientras se renueva no cancela el refresh compartido', () => {
    iniciarSesion('jwt-1');

    const suscripcion = cliente.get('/api/procesos').subscribe({ error: () => undefined });
    rechazar('/api/procesos');
    const refresco = http.expectOne('/api/auth/refresh');
    suscripcion.unsubscribe();

    expect(refresco.cancelled).toBe(false);
    refresco.flush(respuesta('jwt-2'));
    http.expectNone('/api/procesos');
    expect(sesion.tokenAcceso()).toBe('jwt-2');
    expect(sesion.autenticado()).toBe(true);
  });

  it('las escrituras conservan X-XSRF-TOKEN junto al Bearer, también en el reintento', () => {
    iniciarSesion('jwt-1');

    cliente.post('/api/procesos', { nombre: 'Ventas' }).subscribe();
    const original = http.expectOne('/api/procesos');
    expect(original.request.headers.get('Authorization')).toBe('Bearer jwt-1');
    expect(original.request.headers.get('X-XSRF-TOKEN')).toBe('token-csrf');
    original.flush({ codigo: 'X', mensaje: 'x' }, NO_AUTORIZADO);
    const refresco = http.expectOne('/api/auth/refresh');
    expect(refresco.request.headers.get('X-XSRF-TOKEN')).toBe('token-csrf');
    refresco.flush(respuesta('jwt-2'));
    const reintento = http.expectOne('/api/procesos');

    expect(reintento.request.headers.get('Authorization')).toBe('Bearer jwt-2');
    expect(reintento.request.headers.get('X-XSRF-TOKEN')).toBe('token-csrf');
    expect(reintento.request.body).toEqual({ nombre: 'Ventas' });
    reintento.flush({ id: 1 }, { status: 201, statusText: 'Created' });
  });
});

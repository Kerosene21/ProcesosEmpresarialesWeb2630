import { provideHttpClient, withXsrfConfiguration } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  CanActivateFn,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { Observable } from 'rxjs';

import { ROLES_ESCRITURA, RespuestaToken, RolUsuario, Sesion } from '../modelos/sesion';
import { invitadoGuard } from './invitado-guard';
import { rolGuard } from './rol-guard';
import { sesionGuard } from './sesion-guard';

function respuesta(rol: RolUsuario): RespuestaToken {
  const usuario: Sesion = {
    autenticado: true,
    correo: 'editor@alpes.com',
    rol,
    empresaId: 10,
    empresaNombre: 'Alpes',
  };
  return { tokenAcceso: 'jwt-restaurado', tipoToken: 'Bearer', expiraEnSegundos: 900, usuario };
}

describe('guards durante la restauración de la sesión JWT', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=token-csrf; path=/';
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(
          withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
        ),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  function evaluar(
    guard: CanActivateFn,
    url: string,
    roles?: readonly RolUsuario[],
  ): () => boolean | string | undefined {
    const ruta = { data: roles ? { roles } : {} } as unknown as ActivatedRouteSnapshot;
    let resultado: boolean | string | undefined;
    const decision = TestBed.runInInjectionContext(() =>
      guard(ruta, { url } as RouterStateSnapshot),
    ) as Observable<boolean | UrlTree>;
    decision.subscribe((valor) => {
      resultado = valor instanceof UrlTree ? TestBed.inject(Router).serializeUrl(valor) : valor;
    });
    return () => resultado;
  }

  function sesionExpirada(): void {
    http
      .expectOne('/api/auth/refresh')
      .flush(
        { codigo: 'SESION_EXPIRADA', mensaje: 'x' },
        { status: 401, statusText: 'Unauthorized' },
      );
  }

  it('sesionGuard espera la restauración y deja pasar si la sesión sigue vigente', () => {
    const resultado = evaluar(sesionGuard, '/procesos/5');

    expect(resultado()).toBeUndefined();
    http.expectOne('/api/auth/refresh').flush(respuesta('EDITOR'));

    expect(resultado()).toBe(true);
  });

  it('sesionGuard lleva al inicio de sesión recordando la ruta si la sesión expiró', () => {
    const resultado = evaluar(sesionGuard, '/procesos/5');
    sesionExpirada();

    expect(resultado()).toBe('/iniciar-sesion?retorno=%2Fprocesos%2F5');
  });

  it('varios guards durante la misma restauración comparten un solo refresh y no repiten tras expirar', () => {
    const primero = evaluar(sesionGuard, '/procesos');
    const segundo = evaluar(rolGuard, '/procesos/nuevo', ROLES_ESCRITURA);
    sesionExpirada();

    expect(primero()).toBe('/iniciar-sesion?retorno=%2Fprocesos');
    expect(segundo()).toBe('/procesos');
    const tercero = evaluar(sesionGuard, '/mi-empresa');
    http.expectNone('/api/auth/refresh');
    expect(tercero()).toBe('/iniciar-sesion?retorno=%2Fmi-empresa');
  });

  it('rolGuard aplica el rol restaurado desde el servidor', () => {
    const editor = evaluar(rolGuard, '/procesos/nuevo', ROLES_ESCRITURA);
    http.expectOne('/api/auth/refresh').flush(respuesta('EDITOR'));

    expect(editor()).toBe(true);
  });

  it('rolGuard devuelve al listado si el rol restaurado no alcanza', () => {
    const lectura = evaluar(rolGuard, '/usuarios', ['ADMINISTRADOR']);
    http.expectOne('/api/auth/refresh').flush(respuesta('SOLO_LECTURA'));

    expect(lectura()).toBe('/procesos');
  });

  it('invitadoGuard envía al listado a quien recupera su sesión y muestra el login si expiró', () => {
    const recuperada = evaluar(invitadoGuard, '/iniciar-sesion');
    http.expectOne('/api/auth/refresh').flush(respuesta('EDITOR'));
    expect(recuperada()).toBe('/procesos');

    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(
          withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
        ),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const expirada = evaluar(invitadoGuard, '/iniciar-sesion');
    sesionExpirada();
    expect(expirada()).toBe(true);
  });
});

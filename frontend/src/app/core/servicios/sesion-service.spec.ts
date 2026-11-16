import { provideHttpClient, withXsrfConfiguration } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { FalloHttp } from '../modelos/fallo-http';
import { RespuestaToken, SESION_ANONIMA, Sesion } from '../modelos/sesion';
import {
  ALMACEN_MARCAS,
  AlmacenMarcas,
  CANAL_SESION,
  CLAVE_CIERRE_PENDIENTE,
  CanalSesion,
  MensajeSesion,
} from './canal-sesion';
import { SesionService } from './sesion-service';

const AUTENTICADA: Sesion = {
  autenticado: true,
  correo: 'editor@alpes.com',
  rol: 'EDITOR',
  empresaId: 10,
  empresaNombre: 'Alpes Logistica',
};

const CREDENCIALES = { correo: 'editor@alpes.com', password: 'Clave +&=%2026' };
const SIN_CONTENIDO = { status: 204, statusText: 'No Content' };
const NO_AUTORIZADO = { status: 401, statusText: 'Unauthorized' };

function respuesta(token: string, usuario: Sesion = AUTENTICADA): RespuestaToken {
  return { tokenAcceso: token, tipoToken: 'Bearer', expiraEnSegundos: 900, usuario };
}

function conCookieCsrf(valor = 'token-csrf'): void {
  document.cookie = `XSRF-TOKEN=${valor}; path=/`;
}

class CanalFalso implements CanalSesion {
  readonly enviados: MensajeSesion[] = [];
  private readonly oyentes: ((evento: MessageEvent<unknown>) => void)[] = [];

  postMessage(mensaje: MensajeSesion): void {
    this.enviados.push(mensaje);
  }

  addEventListener(_tipo: 'message', oyente: (evento: MessageEvent<unknown>) => void): void {
    this.oyentes.push(oyente);
  }

  recibir(dato: unknown): void {
    this.oyentes.forEach((oyente) => oyente(new MessageEvent('message', { data: dato })));
  }
}

class AlmacenFalso implements AlmacenMarcas {
  readonly valores = new Map<string, string>();

  getItem(clave: string): string | null {
    return this.valores.get(clave) ?? null;
  }

  setItem(clave: string, valor: string): void {
    this.valores.set(clave, valor);
  }

  removeItem(clave: string): void {
    this.valores.delete(clave);
  }
}

describe('SesionService', () => {
  let servicio: SesionService;
  let http: HttpTestingController;
  let canal: CanalFalso;
  let almacen: AlmacenFalso;

  beforeEach(() => {
    canal = new CanalFalso();
    almacen = new AlmacenFalso();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(
          withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
        ),
        provideHttpClientTesting(),
        { provide: CANAL_SESION, useValue: canal },
        { provide: ALMACEN_MARCAS, useValue: almacen },
      ],
    });
    servicio = TestBed.inject(SesionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
    vi.restoreAllMocks();
  });

  function iniciarSesion(token = 'jwt-1'): void {
    conCookieCsrf();
    servicio.iniciar(CREDENCIALES).subscribe();
    http.expectOne('/api/auth/login').flush(respuesta(token));
  }

  it('al recargar obtiene el token CSRF y restaura la sesión con la cookie de refresco', () => {
    let recibida: Sesion | undefined;
    expect(servicio.sesion()).toBeNull();

    servicio.cargar().subscribe((sesion) => (recibida = sesion));
    const csrf = http.expectOne('/api/auth/csrf');
    expect(csrf.request.method).toBe('GET');
    csrf.flush(null, SIN_CONTENIDO);
    const refresco = http.expectOne('/api/auth/refresh');
    expect(refresco.request.method).toBe('POST');
    expect(refresco.request.body).toBeNull();
    expect(refresco.request.headers.has('Authorization')).toBe(false);
    refresco.flush(respuesta('jwt-restaurado'));

    expect(recibida).toEqual(AUTENTICADA);
    expect(servicio.autenticado()).toBe(true);
    expect(servicio.rol()).toBe('EDITOR');
    expect(servicio.tokenAcceso()).toBe('jwt-restaurado');
  });

  it('sin una sesión que restaurar queda anónima sin error', () => {
    conCookieCsrf();
    let recibida: Sesion | undefined;
    let fallo: unknown;

    servicio
      .cargar()
      .subscribe({ next: (sesion) => (recibida = sesion), error: (error) => (fallo = error) });
    http
      .expectOne('/api/auth/refresh')
      .flush({ codigo: 'SESION_EXPIRADA', mensaje: 'x' }, NO_AUTORIZADO);

    expect(fallo).toBeUndefined();
    expect(recibida).toEqual(SESION_ANONIMA);
    expect(servicio.autenticado()).toBe(false);
    expect(servicio.tokenAcceso()).toBeNull();
    expect(servicio.tieneRol(['ADMINISTRADOR', 'EDITOR'])).toBe(false);
  });

  it('propaga un fallo de conexión al restaurar sin inventar una sesión', () => {
    conCookieCsrf();
    let fallo: FalloHttp | undefined;

    servicio.cargar().subscribe({ error: (error) => (fallo = error) });
    http
      .expectOne('/api/auth/refresh')
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

    expect(fallo?.tipo).toBe('conexion');
    expect(servicio.sesion()).toBeNull();
  });

  it('comparte una sola restauración entre llamadas simultáneas y luego reutiliza la sesión', () => {
    conCookieCsrf();
    const recibidas: Sesion[] = [];

    servicio.cargar().subscribe((sesion) => recibidas.push(sesion));
    servicio.asegurar().subscribe((sesion) => recibidas.push(sesion));
    servicio.cargar().subscribe((sesion) => recibidas.push(sesion));
    http.expectOne('/api/auth/refresh').flush(respuesta('jwt-1'));
    servicio.asegurar().subscribe((sesion) => recibidas.push(sesion));

    http.expectNone('/api/auth/refresh');
    expect(recibidas).toEqual([AUTENTICADA, AUTENTICADA, AUTENTICADA, AUTENTICADA]);
  });

  it('inicia sesión con JSON en /api/auth/login y guarda el token solo en memoria', () => {
    conCookieCsrf();
    const guardar = vi.spyOn(Storage.prototype, 'setItem');
    let recibida: Sesion | undefined;

    servicio.iniciar(CREDENCIALES).subscribe((sesion) => (recibida = sesion));
    const inicio = http.expectOne('/api/auth/login');
    expect(inicio.request.method).toBe('POST');
    expect(inicio.request.body).toEqual(CREDENCIALES);
    expect(inicio.request.headers.get('X-XSRF-TOKEN')).toBe('token-csrf');
    expect(inicio.request.headers.has('Authorization')).toBe(false);
    inicio.flush(respuesta('jwt-secreto'));

    expect(recibida).toEqual(AUTENTICADA);
    expect(servicio.tokenAcceso()).toBe('jwt-secreto');
    expect(servicio.tieneRol(['ADMINISTRADOR', 'EDITOR'])).toBe(true);
    expect(guardar).not.toHaveBeenCalled();
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
    expect(document.cookie).not.toContain('jwt-secreto');
  });

  it('obtiene el token CSRF antes de iniciar sesión si todavía no existe la cookie', () => {
    servicio.iniciar(CREDENCIALES).subscribe();

    http.expectOne('/api/auth/csrf').flush(null, SIN_CONTENIDO);
    http.expectOne('/api/auth/login').flush(respuesta('jwt-1'));

    expect(servicio.autenticado()).toBe(true);
  });

  it('traduce las credenciales incorrectas sin crear una sesión', () => {
    conCookieCsrf();
    let fallo: FalloHttp | undefined;

    servicio
      .iniciar({ correo: 'editor@alpes.com', password: 'otra' })
      .subscribe({ error: (error) => (fallo = error) });
    http
      .expectOne('/api/auth/login')
      .flush(
        { codigo: 'CREDENCIALES_INVALIDAS', mensaje: 'Correo o contraseña incorrectos' },
        NO_AUTORIZADO,
      );

    expect(fallo?.tipo).toBe('no-autenticado');
    expect(fallo?.codigo).toBe('CREDENCIALES_INVALIDAS');
    expect(fallo?.detalle).toBe('Correo o contraseña incorrectos');
    expect(servicio.autenticado()).toBe(false);
    expect(servicio.tokenAcceso()).toBeNull();
  });

  it('con un token vigente consulta los datos actuales en /api/auth/me', () => {
    iniciarSesion();
    let recibida: Sesion | undefined;

    servicio.cargar().subscribe((sesion) => (recibida = sesion));
    const consulta = http.expectOne('/api/auth/me');
    expect(consulta.request.method).toBe('GET');
    expect(consulta.request.headers.has('X-XSRF-TOKEN')).toBe(false);
    consulta.flush({ ...AUTENTICADA, rol: 'SOLO_LECTURA' });

    expect(recibida?.rol).toBe('SOLO_LECTURA');
    expect(servicio.rol()).toBe('SOLO_LECTURA');
  });

  it('renovar reemplaza el token en memoria y comparte la petición entre llamadas', () => {
    iniciarSesion('jwt-1');
    const tokens: string[] = [];

    servicio.renovar().subscribe((token) => tokens.push(token));
    servicio.renovar().subscribe((token) => tokens.push(token));
    const refresco = http.expectOne('/api/auth/refresh');
    expect(refresco.request.headers.get('X-XSRF-TOKEN')).toBe('token-csrf');
    refresco.flush(respuesta('jwt-2'));

    expect(tokens).toEqual(['jwt-2', 'jwt-2']);
    expect(servicio.tokenAcceso()).toBe('jwt-2');
  });

  it('cierra la sesión aunque el token haya expirado: limpia el estado al instante y no envía el token', () => {
    iniciarSesion('jwt-expirado');
    let terminado = false;

    servicio.cerrar().subscribe(() => (terminado = true));

    expect(servicio.autenticado()).toBe(false);
    expect(servicio.tokenAcceso()).toBeNull();
    const cierre = http.expectOne('/api/auth/logout');
    expect(cierre.request.method).toBe('POST');
    expect(cierre.request.headers.has('Authorization')).toBe(false);
    expect(cierre.request.headers.get('X-XSRF-TOKEN')).toBe('token-csrf');
    cierre.flush(null, SIN_CONTENIDO);
    http.expectNone('/api/sesion');

    expect(terminado).toBe(true);
    expect(servicio.sesion()).toEqual(SESION_ANONIMA);
  });

  it('si el servidor rechaza el cierre informa el error sin conservar el token', () => {
    iniciarSesion();
    let fallo: FalloHttp | undefined;

    servicio.cerrar().subscribe({ error: (error) => (fallo = error) });
    http
      .expectOne('/api/auth/logout')
      .flush(
        { codigo: 'USUARIO_SIN_PERMISO', mensaje: 'x' },
        { status: 403, statusText: 'Forbidden' },
      );

    expect(fallo?.tipo).toBe('sin-permiso');
    expect(servicio.autenticado()).toBe(false);
    expect(servicio.tokenAcceso()).toBeNull();
  });

  it('una renovación en curso no restaura la sesión después de cerrarla', () => {
    iniciarSesion('jwt-1');
    let fallo: FalloHttp | undefined;

    servicio.renovar().subscribe({ error: (error) => (fallo = error) });
    const refresco = http.expectOne('/api/auth/refresh');
    servicio.cerrar().subscribe();
    http.expectOne('/api/auth/logout').flush(null, SIN_CONTENIDO);
    refresco.flush(respuesta('jwt-tardio'));

    expect(fallo?.codigo).toBe('SESION_DESCARTADA');
    expect(servicio.autenticado()).toBe(false);
    expect(servicio.tokenAcceso()).toBeNull();
  });

  it('una restauración lenta no pisa un inicio de sesión posterior', () => {
    conCookieCsrf();
    servicio.cargar().subscribe();
    const restauracion = http.expectOne('/api/auth/refresh');

    servicio.iniciar(CREDENCIALES).subscribe();
    http.expectOne('/api/auth/login').flush(respuesta('jwt-login'));
    restauracion.flush(respuesta('jwt-viejo', { ...AUTENTICADA, rol: 'SOLO_LECTURA' }));

    expect(servicio.tokenAcceso()).toBe('jwt-login');
    expect(servicio.rol()).toBe('EDITOR');
  });

  it('olvidar elimina el token y deja la sesión anónima', () => {
    iniciarSesion();

    servicio.olvidar();

    expect(servicio.sesion()).toEqual(SESION_ANONIMA);
    expect(servicio.autenticado()).toBe(false);
    expect(servicio.tokenAcceso()).toBeNull();
  });
  it('al cerrar avisa a las demás pestañas y marca el cierre como pendiente hasta que el servidor lo confirma', () => {
    iniciarSesion();

    servicio.cerrar().subscribe();

    expect(canal.enviados).toEqual([{ tipo: 'cerrada' }]);
    expect(servicio.cierrePendiente()).toBe(true);
    http.expectOne('/api/auth/logout').flush(null, SIN_CONTENIDO);
    expect(servicio.cierrePendiente()).toBe(false);
    expect(almacen.valores.size).toBe(0);
  });

  it('un cierre que el servidor no confirma queda pendiente y la recarga no restaura la sesión', () => {
    iniciarSesion();
    let fallo: FalloHttp | undefined;
    servicio.cerrar().subscribe({ error: (error) => (fallo = error) });
    http
      .expectOne('/api/auth/logout')
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

    expect(fallo?.tipo).toBe('conexion');
    expect(servicio.cierrePendiente()).toBe(true);

    let recargada: Sesion | undefined;
    servicio.cargar().subscribe((sesion) => (recargada = sesion));
    http.expectNone('/api/auth/refresh');
    http
      .expectOne('/api/auth/logout')
      .flush(null, { status: 503, statusText: 'Service Unavailable' });

    expect(recargada).toEqual(SESION_ANONIMA);
    expect(servicio.autenticado()).toBe(false);
    expect(servicio.cierrePendiente()).toBe(true);
  });

  it('al recargar con un cierre pendiente lo completa en el servidor antes de cualquier refresh', () => {
    conCookieCsrf();
    almacen.setItem(CLAVE_CIERRE_PENDIENTE, '1');
    let recargada: Sesion | undefined;

    servicio.cargar().subscribe((sesion) => (recargada = sesion));
    http.expectNone('/api/auth/refresh');
    http.expectOne('/api/auth/logout').flush(null, SIN_CONTENIDO);

    expect(recargada).toEqual(SESION_ANONIMA);
    expect(servicio.cierrePendiente()).toBe(false);
  });

  it('reintentar el cierre lo confirma y borra la marca pendiente', () => {
    conCookieCsrf();
    almacen.setItem(CLAVE_CIERRE_PENDIENTE, '1');
    let confirmado = false;

    servicio.reintentarCierre().subscribe(() => (confirmado = true));
    const cierre = http.expectOne('/api/auth/logout');
    expect(cierre.request.headers.get('X-XSRF-TOKEN')).toBe('token-csrf');
    cierre.flush(null, SIN_CONTENIDO);

    expect(confirmado).toBe(true);
    expect(servicio.cierrePendiente()).toBe(false);
  });

  it('un inicio de sesión correcto reemplaza un cierre pendiente', () => {
    almacen.setItem(CLAVE_CIERRE_PENDIENTE, '1');

    iniciarSesion();

    expect(servicio.cierrePendiente()).toBe(false);
  });

  it('un cierre en otra pestaña olvida el token y descarta una renovación en curso', () => {
    iniciarSesion('jwt-1');
    const avisos: number[] = [];
    servicio.cierreEnOtraPestana.subscribe(() => avisos.push(1));
    let fallo: FalloHttp | undefined;
    servicio.renovar().subscribe({ error: (error) => (fallo = error) });
    const refresco = http.expectOne('/api/auth/refresh');

    canal.recibir({ tipo: 'cerrada' });
    refresco.flush(respuesta('jwt-tardio'));

    expect(avisos).toHaveLength(1);
    expect(fallo?.codigo).toBe('SESION_DESCARTADA');
    expect(servicio.autenticado()).toBe(false);
    expect(servicio.tokenAcceso()).toBeNull();
    expect(canal.enviados).toEqual([]);
    http.expectNone('/api/auth/logout');
  });

  it('una pestaña anónima o un mensaje ajeno del canal no provocan avisos', () => {
    const avisos: number[] = [];
    servicio.cierreEnOtraPestana.subscribe(() => avisos.push(1));

    canal.recibir({ tipo: 'cerrada' });
    iniciarSesion();
    canal.recibir({ tipo: 'abierta' });
    canal.recibir('cerrada');
    canal.recibir(null);

    expect(avisos).toHaveLength(0);
    expect(servicio.autenticado()).toBe(true);
  });

  it('sin almacenamiento disponible el cierre sigue funcionando', () => {
    iniciarSesion();
    vi.spyOn(almacen, 'setItem').mockImplementation(() => {
      throw new DOMException('Bloqueado', 'SecurityError');
    });
    vi.spyOn(almacen, 'getItem').mockImplementation(() => {
      throw new DOMException('Bloqueado', 'SecurityError');
    });
    let terminado = false;

    servicio.cerrar().subscribe(() => (terminado = true));
    http.expectOne('/api/auth/logout').flush(null, SIN_CONTENIDO);

    expect(terminado).toBe(true);
    expect(servicio.cierrePendiente()).toBe(false);
    expect(servicio.autenticado()).toBe(false);
  });
});

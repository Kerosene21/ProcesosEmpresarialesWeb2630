import { provideHttpClient, withXsrfConfiguration } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { FalloHttp } from '../modelos/fallo-http';
import { SESION_ANONIMA, Sesion } from '../modelos/sesion';
import { SesionService } from './sesion-service';

const AUTENTICADA: Sesion = {
  autenticado: true,
  correo: 'editor@alpes.com',
  rol: 'EDITOR',
  empresaId: 10,
  empresaNombre: 'Alpes Logistica',
};

describe('SesionService', () => {
  let servicio: SesionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' })),
        provideHttpClientTesting(),
      ],
    });
    servicio = TestBed.inject(SesionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/';
  });

  it('empieza sin conocer la sesión y la carga desde la API', () => {
    expect(servicio.sesion()).toBeNull();
    expect(servicio.autenticado()).toBe(false);

    servicio.cargar().subscribe();
    const solicitud = http.expectOne('/api/sesion');
    expect(solicitud.request.method).toBe('GET');
    solicitud.flush(AUTENTICADA);

    expect(servicio.sesion()).toEqual(AUTENTICADA);
    expect(servicio.autenticado()).toBe(true);
    expect(servicio.rol()).toBe('EDITOR');
  });

  it('reconoce el estado anónimo', () => {
    servicio.cargar().subscribe();
    http.expectOne('/api/sesion').flush(SESION_ANONIMA);

    expect(servicio.autenticado()).toBe(false);
    expect(servicio.rol()).toBeNull();
    expect(servicio.tieneRol(['ADMINISTRADOR', 'EDITOR'])).toBe(false);
  });

  it('reutiliza la sesión ya cargada sin repetir la petición', () => {
    servicio.cargar().subscribe();
    http.expectOne('/api/sesion').flush(AUTENTICADA);
    let recibida: Sesion | undefined;

    servicio.asegurar().subscribe((sesion) => (recibida = sesion));

    http.expectNone('/api/sesion');
    expect(recibida).toEqual(AUTENTICADA);
  });

  it('inicia sesión con un formulario codificado y después recarga la sesión', () => {
    let recibida: Sesion | undefined;

    servicio.iniciar({ correo: 'editor@alpes.com', password: 'Clave +&=%2026' }).subscribe((sesion) => (recibida = sesion));
    const inicio = http.expectOne('/api/sesion');
    expect(inicio.request.method).toBe('POST');
    expect(inicio.request.headers.get('Content-Type')).toBe('application/x-www-form-urlencoded');
    expect(inicio.request.body).toBe('correo=editor%40alpes.com&password=Clave+%2B%26%3D%252026');
    expect(new URLSearchParams(inicio.request.body as string).get('password')).toBe('Clave +&=%2026');
    inicio.flush(null, { status: 204, statusText: 'No Content' });

    const consulta = http.expectOne('/api/sesion');
    expect(consulta.request.method).toBe('GET');
    consulta.flush(AUTENTICADA);
    expect(recibida).toEqual(AUTENTICADA);
    expect(servicio.tieneRol(['ADMINISTRADOR', 'EDITOR'])).toBe(true);
  });

  it('traduce las credenciales incorrectas sin volver a consultar la sesión', () => {
    let fallo: FalloHttp | undefined;

    servicio.iniciar({ correo: 'editor@alpes.com', password: 'otra' }).subscribe({ error: (error) => (fallo = error) });
    http
      .expectOne('/api/sesion')
      .flush(
        { codigo: 'CREDENCIALES_INVALIDAS', mensaje: 'Correo o contraseña incorrectos' },
        { status: 401, statusText: 'Unauthorized' },
      );

    expect(fallo?.tipo).toBe('no-autenticado');
    expect(fallo?.codigo).toBe('CREDENCIALES_INVALIDAS');
    expect(fallo?.detalle).toBe('Correo o contraseña incorrectos');
    expect(servicio.autenticado()).toBe(false);
  });

  it('envía el token de la cookie XSRF-TOKEN en el encabezado de las escrituras y no en las consultas', () => {
    document.cookie = 'XSRF-TOKEN=token-de-prueba; path=/';

    servicio.cargar().subscribe();
    const consulta = http.expectOne('/api/sesion');
    expect(consulta.request.headers.has('X-XSRF-TOKEN')).toBe(false);
    consulta.flush(SESION_ANONIMA);

    let fallo: FalloHttp | undefined;
    servicio.iniciar({ correo: 'editor@alpes.com', password: 'Clave-2026' }).subscribe({ error: (error) => (fallo = error) });
    const inicio = http.expectOne('/api/sesion');
    expect(inicio.request.headers.get('X-XSRF-TOKEN')).toBe('token-de-prueba');
    inicio.flush({ codigo: 'CREDENCIALES_INVALIDAS', mensaje: 'x' }, { status: 401, statusText: 'Unauthorized' });
    expect(fallo?.codigo).toBe('CREDENCIALES_INVALIDAS');
  });

  it('usa el token renovado que el servidor deja en la cookie después de iniciar sesión', () => {
    document.cookie = 'XSRF-TOKEN=token-anonimo; path=/';
    servicio.iniciar({ correo: 'editor@alpes.com', password: 'Clave-2026' }).subscribe();
    const inicio = http.expectOne('/api/sesion');
    expect(inicio.request.headers.get('X-XSRF-TOKEN')).toBe('token-anonimo');
    document.cookie = 'XSRF-TOKEN=token-autenticado; path=/';
    inicio.flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/sesion').flush(AUTENTICADA);

    servicio.cerrar().subscribe();

    const cierre = http.expectOne('/api/sesion');
    expect(cierre.request.method).toBe('DELETE');
    expect(cierre.request.headers.get('X-XSRF-TOKEN')).toBe('token-autenticado');
    cierre.flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/sesion').flush(SESION_ANONIMA);
  });

  it('cierra la sesión, queda anónima y obtiene un token nuevo con la consulta posterior', () => {
    servicio.cargar().subscribe();
    http.expectOne('/api/sesion').flush(AUTENTICADA);
    let terminado = false;

    servicio.cerrar().subscribe(() => (terminado = true));
    http.expectOne((peticion) => peticion.method === 'DELETE').flush(null, { status: 204, statusText: 'No Content' });
    const consulta = http.expectOne((peticion) => peticion.method === 'GET');
    consulta.flush(SESION_ANONIMA);

    expect(terminado).toBe(true);
    expect(servicio.autenticado()).toBe(false);
  });

  it('informa el error si el servidor rechaza el cierre y conserva la sesión', () => {
    servicio.cargar().subscribe();
    http.expectOne('/api/sesion').flush(AUTENTICADA);
    let fallo: FalloHttp | undefined;

    servicio.cerrar().subscribe({ error: (error) => (fallo = error) });
    http
      .expectOne('/api/sesion')
      .flush({ codigo: 'USUARIO_SIN_PERMISO', mensaje: 'x' }, { status: 403, statusText: 'Forbidden' });

    expect(fallo?.tipo).toBe('sin-permiso');
    expect(servicio.autenticado()).toBe(true);
  });

  it('olvida la sesión localmente cuando expira', () => {
    servicio.cargar().subscribe();
    http.expectOne('/api/sesion').flush(AUTENTICADA);

    servicio.olvidar();

    expect(servicio.sesion()).toEqual(SESION_ANONIMA);
    expect(servicio.autenticado()).toBe(false);
  });

  it('traduce un fallo de conexión al consultar la sesión', () => {
    let fallo: FalloHttp | undefined;

    servicio.cargar().subscribe({ error: (error) => (fallo = error) });
    http.expectOne('/api/sesion').error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

    expect(fallo?.tipo).toBe('conexion');
    expect(servicio.sesion()).toBeNull();
  });
});

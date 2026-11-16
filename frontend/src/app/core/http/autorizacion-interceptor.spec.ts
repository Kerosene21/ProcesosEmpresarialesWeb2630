import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { SesionService } from '../servicios/sesion-service';
import { autorizacionInterceptor } from './autorizacion-interceptor';

describe('autorizacionInterceptor', () => {
  let http: HttpTestingController;
  let cliente: HttpClient;
  let token: string | null;

  beforeEach(() => {
    token = 'jwt-actual';
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([autorizacionInterceptor])),
        provideHttpClientTesting(),
        { provide: SesionService, useValue: { tokenAcceso: () => token } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    cliente = TestBed.inject(HttpClient);
  });

  afterEach(() => http.verify());

  function cabecera(url: string, metodo: 'GET' | 'POST' = 'GET'): string | null {
    (metodo === 'GET' ? cliente.get(url) : cliente.post(url, null)).subscribe();
    const peticion = http.expectOne(url);
    peticion.flush(null);
    return peticion.request.headers.get('Authorization');
  }

  it('añade el Bearer a los endpoints protegidos de la API', () => {
    expect(cabecera('/api/procesos')).toBe('Bearer jwt-actual');
    expect(cabecera('/api/procesos/5/diagrama?alcance=TODOS')).toBe('Bearer jwt-actual');
    expect(cabecera('/api/usuarios', 'POST')).toBe('Bearer jwt-actual');
  });

  it('añade el Bearer a /api/auth/me aunque pertenezca a /api/auth', () => {
    expect(cabecera('/api/auth/me')).toBe('Bearer jwt-actual');
  });

  it('no envía el Bearer a csrf, login, refresh ni logout', () => {
    expect(cabecera('/api/auth/csrf')).toBeNull();
    expect(cabecera('/api/auth/login', 'POST')).toBeNull();
    expect(cabecera('/api/auth/refresh', 'POST')).toBeNull();
    expect(cabecera('/api/auth/logout', 'POST')).toBeNull();
  });

  it('no envía el token a dominios externos ni a recursos que no son de la API', () => {
    expect(cabecera('https://externo.example.com/api/procesos')).toBeNull();
    expect(cabecera('//externo.example.com/api/procesos')).toBeNull();
    expect(cabecera('/assets/configuracion.json')).toBeNull();
    expect(cabecera('/apiprocesos')).toBeNull();
  });

  it('sin token en memoria no añade Authorization', () => {
    token = null;

    expect(cabecera('/api/procesos')).toBeNull();
  });
});

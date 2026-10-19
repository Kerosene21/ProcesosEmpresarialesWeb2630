import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Empresa, RegistroEmpresa } from '../modelos/empresa';
import { FalloHttp } from '../modelos/fallo-http';
import { EmpresaService } from './empresa-service';

const REGISTRO: RegistroEmpresa = {
  nombre: 'Alpes Logistica',
  nit: '900123456-7',
  correoContacto: 'admin@alpes.com',
  passwordAdministrador: 'Clave-Inicial-2026',
};

const EMPRESA: Empresa = {
  id: 10,
  nombre: 'Alpes Logistica',
  nit: '900123456-7',
  correoContacto: 'admin@alpes.com',
  administradorUsername: 'admin@alpes.com',
};

describe('EmpresaService', () => {
  let servicio: EmpresaService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(EmpresaService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('registra la empresa enviando el contrato del DTO', () => {
    let registrada: Empresa | undefined;

    servicio.registrar(REGISTRO).subscribe((empresa) => (registrada = empresa));

    const solicitud = http.expectOne('/api/empresas');
    expect(solicitud.request.method).toBe('POST');
    expect(solicitud.request.body).toEqual(REGISTRO);
    solicitud.flush(EMPRESA, { status: 201, statusText: 'Created' });
    expect(registrada).toEqual(EMPRESA);
  });

  it('traduce el NIT duplicado como conflicto con su código', () => {
    let fallo: FalloHttp | undefined;

    servicio.registrar(REGISTRO).subscribe({ error: (error) => (fallo = error) });
    http
      .expectOne('/api/empresas')
      .flush({ codigo: 'EMPRESA_NIT_DUPLICADO', mensaje: 'Ya existe' }, { status: 409, statusText: 'Conflict' });

    expect(fallo?.tipo).toBe('conflicto');
    expect(fallo?.codigo).toBe('EMPRESA_NIT_DUPLICADO');
  });

  it('conserva los errores de validación por campo que devuelve el backend', () => {
    let fallo: FalloHttp | undefined;

    servicio.registrar(REGISTRO).subscribe({ error: (error) => (fallo = error) });
    http
      .expectOne('/api/empresas')
      .flush(
        { codigo: 'DATOS_NO_VALIDOS', mensaje: 'Hay campos con valores no válidos', campos: { nit: 'El NIT es obligatorio' } },
        { status: 400, statusText: 'Bad Request' },
      );

    expect(fallo?.tipo).toBe('datos-no-validos');
    expect(fallo?.campos).toEqual({ nit: 'El NIT es obligatorio' });
  });

  it('consulta la empresa del usuario autenticado', () => {
    let empresa: Empresa | undefined;

    servicio.actual().subscribe((respuesta) => (empresa = respuesta));

    const solicitud = http.expectOne('/api/empresas/actual');
    expect(solicitud.request.method).toBe('GET');
    solicitud.flush(EMPRESA);
    expect(empresa).toEqual(EMPRESA);
  });
});

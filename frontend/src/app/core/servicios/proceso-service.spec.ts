import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { FalloHttp } from '../modelos/fallo-http';
import { Pagina } from '../modelos/pagina';
import { ProcesoResumen } from '../modelos/proceso';
import { ProcesoService } from './proceso-service';

describe('ProcesoService', () => {
  let servicio: ProcesoService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(ProcesoService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista procesos enviando solo los filtros con valor', () => {
    const pagina: Pagina<ProcesoResumen> = {
      content: [],
      page: { size: 10, number: 2, totalElements: 21, totalPages: 3 },
    };
    let recibida: Pagina<ProcesoResumen> | undefined;

    servicio
      .listar({ q: '  ventas ', estado: 'PUBLICADO', categoria: '', visibilidad: null, alcance: 'TODOS', page: 2 })
      .subscribe((respuesta) => (recibida = respuesta));

    const solicitud = http.expectOne((peticion) => peticion.url === '/api/procesos');
    expect(solicitud.request.method).toBe('GET');
    expect(solicitud.request.params.keys().sort()).toEqual(['alcance', 'estado', 'page', 'q']);
    expect(solicitud.request.params.get('q')).toBe('ventas');
    expect(solicitud.request.params.get('page')).toBe('2');
    solicitud.flush(pagina);
    expect(recibida).toEqual(pagina);
  });

  it('envía la página cero porque es un valor válido', () => {
    servicio.listar({ page: 0 }).subscribe();

    const solicitud = http.expectOne((peticion) => peticion.url === '/api/procesos');
    expect(solicitud.request.params.get('page')).toBe('0');
    solicitud.flush({ content: [], page: { size: 10, number: 0, totalElements: 0, totalPages: 0 } });
  });

  it('consulta un proceso y su diagrama por identificador', () => {
    servicio.obtener(7).subscribe();
    servicio.obtenerDiagrama(7).subscribe();

    expect(http.expectOne('/api/procesos/7').request.method).toBe('GET');
    expect(http.expectOne('/api/procesos/7/diagrama').request.method).toBe('GET');
  });

  it('prepara la creación y la edición con los cuerpos de los DTO', () => {
    servicio.crear({ nombre: 'Compras', descripcion: 'Gestión de compras', categoria: 'Finanzas' }).subscribe();
    servicio
      .editar(7, { nombre: 'Compras', descripcion: 'Gestión de compras', categoria: 'Finanzas', estado: 'PUBLICADO' })
      .subscribe();

    const creacion = http.expectOne((peticion) => peticion.method === 'POST');
    expect(creacion.request.url).toBe('/api/procesos');
    expect(creacion.request.body).toEqual({ nombre: 'Compras', descripcion: 'Gestión de compras', categoria: 'Finanzas' });
    const edicion = http.expectOne((peticion) => peticion.method === 'PUT');
    expect(edicion.request.url).toBe('/api/procesos/7');
    expect(edicion.request.body.estado).toBe('PUBLICADO');
  });

  it('convierte una respuesta 401 del backend en un fallo tipado', () => {
    let fallo: FalloHttp | undefined;

    servicio.obtener(7).subscribe({ error: (error: FalloHttp) => (fallo = error) });
    http
      .expectOne('/api/procesos/7')
      .flush(
        { codigo: 'USUARIO_NO_AUTORIZADO', mensaje: 'Se requiere autenticacion' },
        { status: 401, statusText: 'Unauthorized' },
      );

    expect(fallo).toEqual({
      tipo: 'no-autenticado',
      estado: 401,
      codigo: 'USUARIO_NO_AUTORIZADO',
      titulo: 'Se requiere iniciar sesión',
      detalle: 'Se requiere autenticacion',
    });
  });

  it('elimina el proceso con DELETE y completa con la respuesta 204', () => {
    let terminado = false;

    servicio.eliminar(7).subscribe({ complete: () => (terminado = true) });

    const solicitud = http.expectOne('/api/procesos/7');
    expect(solicitud.request.method).toBe('DELETE');
    solicitud.flush(null, { status: 204, statusText: 'No Content' });
    expect(terminado).toBe(true);
  });

  it('traduce el rechazo de la eliminación por falta de permisos', () => {
    let fallo: FalloHttp | undefined;

    servicio.eliminar(7).subscribe({ error: (error: FalloHttp) => (fallo = error) });
    http
      .expectOne('/api/procesos/7')
      .flush(
        { codigo: 'USUARIO_SIN_PERMISO', mensaje: 'Solo un administrador puede eliminar procesos' },
        { status: 403, statusText: 'Forbidden' },
      );

    expect(fallo?.tipo).toBe('sin-permiso');
    expect(fallo?.detalle).toBe('Solo un administrador puede eliminar procesos');
  });

  it('consulta el historial con el contrato de HistorialProcesoRespuestaDto', () => {
    const historial = [
      {
        fecha: '2026-10-18T10:15:00',
        usuarioCorreo: 'admin@alpes.com',
        estadoAnterior: 'BORRADOR',
        cambiosRealizados: 'estado: BORRADOR -> PUBLICADO',
      },
    ];
    let recibido: unknown;

    servicio.historial(7).subscribe((respuesta) => (recibido = respuesta));

    const solicitud = http.expectOne('/api/procesos/7/historial');
    expect(solicitud.request.method).toBe('GET');
    solicitud.flush(historial);
    expect(recibido).toEqual(historial);
  });
});

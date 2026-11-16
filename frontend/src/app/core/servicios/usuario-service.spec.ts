import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { FalloHttp } from '../modelos/fallo-http';
import { Usuario } from '../modelos/usuario';
import { UsuarioService } from './usuario-service';

describe('UsuarioService', () => {
  let servicio: UsuarioService;
  let http: HttpTestingController;
  const ruta = '/api/usuarios';

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(UsuarioService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista y consulta los usuarios de la empresa sin paginación inventada', () => {
    let lista: Usuario[] | undefined;

    servicio.listar().subscribe((usuarios) => (lista = usuarios));
    servicio.obtener(15).subscribe();

    const listado = http.expectOne(ruta);
    expect(listado.request.method).toBe('GET');
    expect(listado.request.params.keys()).toEqual([]);
    listado.flush([{ id: 15, correo: 'editor@alpes.com', rol: 'EDITOR', activo: true }]);
    expect(lista).toEqual([{ id: 15, correo: 'editor@alpes.com', rol: 'EDITOR', activo: true }]);
    expect(http.expectOne(`${ruta}/15`).request.method).toBe('GET');
  });

  it('crea el usuario con correo, contraseña inicial y rol', () => {
    servicio.crear({ correo: 'editor@alpes.com', password: 'Clave-Inicial-2026', rol: 'EDITOR' }).subscribe();

    const peticion = http.expectOne(ruta);
    expect(peticion.request.method).toBe('POST');
    expect(peticion.request.body).toEqual({ correo: 'editor@alpes.com', password: 'Clave-Inicial-2026', rol: 'EDITOR' });
  });

  it('cambia el rol y desactiva con las rutas propias de cada acción', () => {
    servicio.cambiarRol(15, { rol: 'SOLO_LECTURA' }).subscribe();
    servicio.desactivar(15).subscribe();

    const cambio = http.expectOne(`${ruta}/15/rol`);
    expect(cambio.request.method).toBe('PUT');
    expect(cambio.request.body).toEqual({ rol: 'SOLO_LECTURA' });
    const desactivacion = http.expectOne(`${ruta}/15/desactivacion`);
    expect(desactivacion.request.method).toBe('POST');
    expect(desactivacion.request.body).toBeNull();
  });

  it('traduce los errores 400, 401, 403 y 409', () => {
    const fallos: FalloHttp[] = [];
    const guardar = { error: (fallo: FalloHttp) => fallos.push(fallo) };
    const crear = () =>
      servicio.crear({ correo: 'editor@alpes.com', password: 'Clave-Inicial-2026', rol: 'EDITOR' }).subscribe(guardar);

    crear();
    http
      .expectOne(ruta)
      .flush(
        { codigo: 'DATOS_NO_VALIDOS', mensaje: 'Hay campos con valores no válidos', campos: { correo: 'El correo no tiene un formato valido' } },
        { status: 400, statusText: 'Bad Request' },
      );
    servicio.listar().subscribe(guardar);
    http.expectOne(ruta).flush({ codigo: 'USUARIO_NO_AUTORIZADO', mensaje: 'Se requiere autenticacion' }, { status: 401, statusText: 'Unauthorized' });
    servicio.listar().subscribe(guardar);
    http.expectOne(ruta).flush({ codigo: 'USUARIO_SIN_PERMISO', mensaje: 'No tienes permiso para realizar esta accion' }, { status: 403, statusText: 'Forbidden' });
    crear();
    http
      .expectOne(ruta)
      .flush({ codigo: 'USUARIO_CORREO_EN_USO', mensaje: 'El correo editor@alpes.com ya esta asociado a otro usuario' }, { status: 409, statusText: 'Conflict' });

    expect(fallos).toMatchObject([
      { tipo: 'datos-no-validos', campos: { correo: 'El correo no tiene un formato valido' } },
      { tipo: 'no-autenticado' },
      { tipo: 'sin-permiso', codigo: 'USUARIO_SIN_PERMISO' },
      { tipo: 'conflicto', codigo: 'USUARIO_CORREO_EN_USO' },
    ]);
  });
});

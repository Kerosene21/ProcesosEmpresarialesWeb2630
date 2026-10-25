import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { FalloHttp } from '../../core/modelos/fallo-http';
import { RolUsuario, Sesion } from '../../core/modelos/sesion';
import { Usuario } from '../../core/modelos/usuario';
import { SesionService } from '../../core/servicios/sesion-service';
import { UsuarioService } from '../../core/servicios/usuario-service';
import { ListaUsuarios } from './lista-usuarios';

const SESION: Sesion = {
  autenticado: true,
  correo: 'admin@alpes.com',
  rol: 'ADMINISTRADOR',
  empresaId: 10,
  empresaNombre: 'Alpes',
};

const USUARIOS: Usuario[] = [
  { id: 1, correo: 'admin@alpes.com', rol: 'ADMINISTRADOR', activo: true },
  { id: 15, correo: 'editor@alpes.com', rol: 'EDITOR', activo: true },
  { id: 16, correo: 'antiguo@alpes.com', rol: 'SOLO_LECTURA', activo: false },
];

const SIN_PERMISO: FalloHttp = {
  tipo: 'sin-permiso',
  estado: 403,
  codigo: 'USUARIO_SIN_PERMISO',
  titulo: 'No tienes permiso para esta consulta',
  detalle: 'No tienes permiso para realizar esta accion',
};

describe('ListaUsuarios', () => {
  let fixture: ComponentFixture<ListaUsuarios>;
  let elemento: HTMLElement;
  const listar = vi.fn<() => Observable<Usuario[]>>();

  async function crear(respuesta: Observable<Usuario[]>, rol: RolUsuario = 'ADMINISTRADOR'): Promise<void> {
    listar.mockReset().mockReturnValue(respuesta);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: UsuarioService, useValue: { listar } },
        {
          provide: SesionService,
          useValue: { sesion: signal({ ...SESION, rol }), tieneRol: (roles: readonly RolUsuario[]) => roles.includes(rol) },
        },
      ],
    });
    fixture = TestBed.createComponent(ListaUsuarios);
    elemento = fixture.nativeElement;
    await fixture.whenStable();
  }

  function filas(): string[][] {
    return Array.from(elemento.querySelectorAll('tbody tr')).map((fila) =>
      Array.from(fila.querySelectorAll('td')).map((celda) => (celda.querySelector('a') ?? celda).textContent?.trim() ?? ''),
    );
  }

  it('muestra correo, rol y estado de cada usuario consultado a la API', async () => {
    await crear(of(USUARIOS));

    expect(listar).toHaveBeenCalledTimes(1);
    expect(filas()).toEqual([
      ['admin@alpes.com', 'Administrador', 'Activo'],
      ['editor@alpes.com', 'Editor', 'Activo'],
      ['antiguo@alpes.com', 'Solo lectura', 'Inactivo'],
    ]);
    expect(Array.from(elemento.querySelectorAll('tbody .chip--informacion')).map((chip) => chip.closest('tr')?.querySelector('a')?.textContent)).toEqual([
      'admin@alpes.com',
    ]);
    expect(elemento.textContent).toContain('3 usuarios · 2 activos');
    expect(elemento.querySelector('a[href="/usuarios/15"]')?.textContent).toBe('editor@alpes.com');
    expect(elemento.querySelector('a[href="/usuarios/nuevo"]')).not.toBeNull();
  });

  it('muestra el estado vacío', async () => {
    await crear(of([]));

    expect(elemento.querySelector('[role="status"]')?.textContent).toContain('La empresa todavía no tiene usuarios');
    expect(elemento.querySelector('table')).toBeNull();
  });

  it('informa el rechazo del servidor y permite reintentar', async () => {
    await crear(throwError(() => SIN_PERMISO));

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('No tienes permiso para esta consulta');
    listar.mockReturnValue(of(USUARIOS));
    elemento.querySelector<HTMLButtonElement>('.aviso__accion')!.click();
    await fixture.whenStable();

    expect(listar).toHaveBeenCalledTimes(2);
    expect(filas()).toHaveLength(3);
  });

  it('no ofrece acciones administrativas a quien no es administrador', async () => {
    await crear(throwError(() => SIN_PERMISO), 'EDITOR');

    expect(elemento.querySelector('a[href="/usuarios/nuevo"]')).toBeNull();
  });
});

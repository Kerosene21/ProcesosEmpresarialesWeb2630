import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { FalloHttp } from '../../core/modelos/fallo-http';
import { RolUsuario } from '../../core/modelos/sesion';
import { CambiarRolUsuario, Usuario } from '../../core/modelos/usuario';
import { SesionService } from '../../core/servicios/sesion-service';
import { UsuarioService } from '../../core/servicios/usuario-service';
import { DetalleUsuario } from './detalle-usuario';

const EDITOR: Usuario = { id: 15, correo: 'editor@alpes.com', rol: 'EDITOR', activo: true };

function fallo(tipo: FalloHttp['tipo'], estado: number, codigo: string, detalle: string): FalloHttp {
  return { tipo, estado, codigo, titulo: 'Rechazado', detalle };
}

describe('DetalleUsuario', () => {
  let fixture: ComponentFixture<DetalleUsuario>;
  let elemento: HTMLElement;
  const obtener = vi.fn<(id: number) => Observable<Usuario>>();
  const cambiarRol = vi.fn<(id: number, datos: CambiarRolUsuario) => Observable<Usuario>>();
  const desactivar = vi.fn<(id: number) => Observable<Usuario>>();

  async function crear(respuesta: Observable<Usuario>, rol: RolUsuario = 'ADMINISTRADOR', id = '15'): Promise<void> {
    obtener.mockReset().mockReturnValue(respuesta);
    cambiarRol.mockReset();
    desactivar.mockReset();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: UsuarioService, useValue: { obtener, cambiarRol, desactivar } },
        {
          provide: SesionService,
          useValue: {
            sesion: signal({ autenticado: true, correo: 'admin@alpes.com', rol, empresaId: 10, empresaNombre: 'Alpes' }),
            tieneRol: (roles: readonly RolUsuario[]) => roles.includes(rol),
          },
        },
      ],
    });
    fixture = TestBed.createComponent(DetalleUsuario);
    elemento = fixture.nativeElement;
    fixture.componentRef.setInput('id', id);
    await fixture.whenStable();
  }

  function boton(texto: string): HTMLButtonElement | undefined {
    return Array.from(elemento.querySelectorAll('button')).find((actual) => actual.textContent?.trim() === texto);
  }

  function chips(): string[] {
    return Array.from(elemento.querySelectorAll('.encabezado-pagina .chip')).map((chip) => chip.textContent?.trim() ?? '');
  }

  async function elegirRol(texto: string): Promise<void> {
    const lista = elemento.querySelector<HTMLSelectElement>('#campo-rol-usuario')!;
    lista.selectedIndex = Array.from(lista.options).findIndex((opcion) => opcion.textContent?.trim() === texto);
    lista.dispatchEvent(new Event('change'));
    await fixture.whenStable();
  }

  it('consulta el usuario y muestra su rol y su estado', async () => {
    await crear(of(EDITOR));

    expect(obtener).toHaveBeenCalledWith(15);
    expect(elemento.querySelector('h1')?.textContent).toBe('editor@alpes.com');
    expect(chips()).toEqual(['Editor', 'Activo']);
    expect(elemento.querySelector<HTMLSelectElement>('#campo-rol-usuario')!.selectedOptions[0].textContent?.trim()).toBe('Editor');
    expect(boton('Guardar rol')!.disabled).toBe(true);
  });

  it('cambia el rol con la API y muestra el rol persistido', async () => {
    await crear(of(EDITOR));
    cambiarRol.mockReturnValue(of({ ...EDITOR, rol: 'SOLO_LECTURA' }));
    await elegirRol('Solo lectura');
    elemento.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    expect(cambiarRol).toHaveBeenCalledWith(15, { rol: 'SOLO_LECTURA' });
    expect(chips()).toEqual(['Solo lectura', 'Activo']);
    expect(elemento.querySelector('.detalle-usuario__exito')?.textContent).toContain('ahora es Solo lectura');
  });

  it('muestra el rechazo del cambio de rol que devuelve el servidor', async () => {
    await crear(of(EDITOR));
    cambiarRol.mockReturnValue(throwError(() => fallo('sin-permiso', 403, 'USUARIO_SIN_PERMISO', 'No tienes permiso para realizar esta accion')));
    await elegirRol('Administrador');
    elemento.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('No tienes permiso para realizar esta accion');
    expect(chips()).toEqual(['Editor', 'Activo']);
  });

  it('desactiva tras confirmar y conserva al usuario como inactivo', async () => {
    await crear(of(EDITOR));
    desactivar.mockReturnValue(of({ ...EDITOR, activo: false }));

    boton('Desactivar usuario')!.click();
    await fixture.whenStable();
    expect(elemento.querySelector('[role="alertdialog"]')?.textContent).toContain('La desactivación es lógica');
    expect(desactivar).not.toHaveBeenCalled();
    elemento.querySelectorAll<HTMLButtonElement>('[role="alertdialog"] button')[1].click();
    await fixture.whenStable();

    expect(desactivar).toHaveBeenCalledWith(15);
    expect(elemento.querySelector('[role="alertdialog"]')).toBeNull();
    expect(chips()).toEqual(['Editor', 'Inactivo']);
    expect(boton('Desactivar usuario')).toBeUndefined();
    expect(elemento.textContent).toContain('no permite reactivarlo');
  });

  it('mantiene abierto el diálogo con el error si la desactivación se rechaza', async () => {
    await crear(of(EDITOR));
    desactivar.mockReturnValue(throwError(() => fallo('no-encontrado', 404, 'RECURSO_NO_ENCONTRADO', 'El usuario no existe en tu empresa')));

    boton('Desactivar usuario')!.click();
    await fixture.whenStable();
    elemento.querySelectorAll<HTMLButtonElement>('[role="alertdialog"] button')[1].click();
    await fixture.whenStable();

    expect(elemento.querySelector('.dialogo__error')?.textContent).toContain('El usuario no existe en tu empresa');
    expect(chips()).toEqual(['Editor', 'Activo']);
  });

  it('en la propia cuenta no ofrece cambiar el rol ni desactivarla', async () => {
    await crear(of({ id: 1, correo: 'admin@alpes.com', rol: 'ADMINISTRADOR', activo: true }), 'ADMINISTRADOR', '1');

    expect(chips()).toContain('Tu cuenta');
    expect(elemento.textContent).toContain('Un administrador no puede cambiar su propio rol');
    expect(elemento.querySelector('#campo-rol-usuario')).toBeNull();
    expect(boton('Desactivar usuario')).toBeUndefined();
  });

  it('no habilita acciones administrativas sin el rol de administrador', async () => {
    await crear(of(EDITOR), 'EDITOR');

    expect(elemento.querySelector('#campo-rol-usuario')).toBeNull();
    expect(boton('Desactivar usuario')).toBeUndefined();
  });

  it('informa el usuario inexistente o de otra empresa y permite reintentar', async () => {
    await crear(throwError(() => fallo('no-encontrado', 404, 'RECURSO_NO_ENCONTRADO', 'El usuario no existe en tu empresa')));

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('Rechazado');
    obtener.mockReturnValue(of(EDITOR));
    elemento.querySelector<HTMLButtonElement>('.aviso__accion')!.click();
    await fixture.whenStable();

    expect(obtener).toHaveBeenCalledTimes(2);
    expect(elemento.querySelector('h1')?.textContent).toBe('editor@alpes.com');
  });

  it('rechaza un identificador no válido sin consultar la API', async () => {
    await crear(of(EDITOR), 'ADMINISTRADOR', 'abc');

    expect(obtener).not.toHaveBeenCalled();
    expect(elemento.textContent).toContain('El identificador del usuario no es válido');
  });
});

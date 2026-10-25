import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';
import type { MockInstance } from 'vitest';

import { FalloHttp } from '../../core/modelos/fallo-http';
import { CrearRolProceso, RolProceso, RolProcesoResumen } from '../../core/modelos/rol-proceso';
import { RolProcesoService } from '../../core/servicios/rol-proceso-service';
import { FormularioRol } from './formulario-rol';

const ROL: RolProcesoResumen = {
  id: 21,
  nombre: 'Supervisor',
  descripcion: 'Aprueba las solicitudes evaluadas',
  activo: true,
  enUso: false,
  puedeEliminar: true,
  procesos: [],
};

describe('FormularioRol', () => {
  let fixture: ComponentFixture<FormularioRol>;
  let elemento: HTMLElement;
  let navegar: MockInstance;
  const obtener = vi.fn<(id: number) => Observable<RolProcesoResumen>>();
  const crearRol = vi.fn<(datos: CrearRolProceso) => Observable<RolProceso>>();
  const editar = vi.fn<(id: number, datos: CrearRolProceso) => Observable<RolProceso>>();

  async function crear(id?: string, rol: RolProcesoResumen = ROL): Promise<void> {
    obtener.mockReset().mockReturnValue(of(rol));
    crearRol.mockReset();
    editar.mockReset();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: RolProcesoService, useValue: { obtener, crear: crearRol, editar } }],
    });
    navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(FormularioRol);
    elemento = fixture.nativeElement;
    if (id !== undefined) {
      fixture.componentRef.setInput('id', id);
    }
    await fixture.whenStable();
  }

  function escribir(selector: string, valor: string): void {
    const campo = elemento.querySelector<HTMLInputElement | HTMLTextAreaElement>(selector)!;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
  }

  async function enviar(): Promise<void> {
    elemento.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  function error(campo: string): string | undefined {
    return elemento.querySelector(`#pie-rol-${campo} .campo__error`)?.textContent?.trim();
  }

  it('valida los campos obligatorios y las longitudes del DTO sin llamar a la API', async () => {
    await crear();
    escribir('#campo-rol-nombre', 'x'.repeat(101));
    escribir('#campo-rol-descripcion', '   ');
    await enviar();

    expect(crearRol).not.toHaveBeenCalled();
    expect(error('nombre')).toBe('El nombre no puede superar 100 caracteres');
    expect(error('descripcion')).toBe('La descripción es obligatoria');
  });

  it('crea el rol con POST y navega a su detalle', async () => {
    await crear();
    crearRol.mockReturnValue(of({ ...ROL, id: 30 }));
    escribir('#campo-rol-nombre', 'Analista');
    escribir('#campo-rol-descripcion', 'Registra las solicitudes');
    await enviar();

    expect(crearRol).toHaveBeenCalledWith({ nombre: 'Analista', descripcion: 'Registra las solicitudes' });
    expect(navegar).toHaveBeenCalledWith(['/roles-proceso', 30]);
  });

  it('carga el rol existente y guarda los cambios con PUT', async () => {
    await crear('21');
    editar.mockReturnValue(of(ROL));
    expect(obtener).toHaveBeenCalledWith(21);
    expect(elemento.querySelector<HTMLInputElement>('#campo-rol-nombre')?.value).toBe('Supervisor');

    escribir('#campo-rol-descripcion', 'Aprueba y archiva');
    await enviar();

    expect(editar).toHaveBeenCalledWith(21, { nombre: 'Supervisor', descripcion: 'Aprueba y archiva' });
    expect(elemento.querySelector('button[type="submit"]')?.textContent?.trim()).toBe('Guardando…');
  });

  it('muestra el nombre duplicado en su campo', async () => {
    await crear();
    crearRol.mockReturnValue(
      throwError((): FalloHttp => ({
        tipo: 'conflicto',
        estado: 409,
        codigo: 'ROL_PROCESO_NOMBRE_DUPLICADO',
        titulo: 'La operación entra en conflicto con los datos actuales',
        detalle: 'Ya existe un rol de proceso con ese nombre en la empresa',
      })),
    );
    escribir('#campo-rol-nombre', 'Supervisor');
    escribir('#campo-rol-descripcion', 'Otro');
    await enviar();

    expect(error('nombre')).toBe('Ya existe un rol de proceso con ese nombre en la empresa');
    expect(navegar).not.toHaveBeenCalled();
  });

  it('muestra los errores 400 por campo y el rechazo 403 del backend', async () => {
    await crear();
    crearRol.mockReturnValue(
      throwError((): FalloHttp => ({
        tipo: 'datos-no-validos',
        estado: 400,
        codigo: 'DATOS_NO_VALIDOS',
        titulo: 'Los datos enviados no son válidos',
        detalle: 'Hay campos con valores no válidos',
        campos: { descripcion: 'La descripción es obligatoria' },
      })),
    );
    escribir('#campo-rol-nombre', 'Analista');
    escribir('#campo-rol-descripcion', 'Texto');
    await enviar();
    expect(error('descripcion')).toBe('La descripción es obligatoria');

    crearRol.mockReturnValue(
      throwError((): FalloHttp => ({
        tipo: 'sin-permiso',
        estado: 403,
        codigo: 'USUARIO_SIN_PERMISO',
        titulo: 'No tienes permiso para esta consulta',
        detalle: 'Solo un administrador puede crear roles de proceso',
      })),
    );
    escribir('#campo-rol-descripcion', 'Texto válido');
    await enviar();

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('Solo un administrador puede crear roles de proceso');
  });

  it('bloquea la edición de un rol eliminado', async () => {
    await crear('21', { ...ROL, activo: false });

    expect(elemento.textContent).toContain('Este rol de proceso no se puede editar');
    expect(elemento.querySelector<HTMLInputElement>('#campo-rol-nombre')?.disabled).toBe(true);
    expect(elemento.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(true);
  });

  it('muestra el 403 de un rol de otra empresa sin formulario', async () => {
    obtener.mockReset();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: RolProcesoService, useValue: { obtener, crear: crearRol, editar } }],
    });
    obtener.mockReturnValue(
      throwError((): FalloHttp => ({
        tipo: 'sin-permiso',
        estado: 403,
        codigo: 'USUARIO_SIN_PERMISO',
        titulo: 'No tienes permiso para esta consulta',
        detalle: 'El rol de proceso no pertenece a la empresa del usuario',
      })),
    );
    fixture = TestBed.createComponent(FormularioRol);
    elemento = fixture.nativeElement;
    fixture.componentRef.setInput('id', '99');
    await fixture.whenStable();

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('No tienes permiso para esta consulta');
    expect(elemento.querySelector('form')).toBeNull();
  });
});

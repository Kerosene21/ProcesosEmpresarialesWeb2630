import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { NEVER, of, throwError } from 'rxjs';
import type { MockInstance } from 'vitest';

import { FalloHttp } from '../../core/modelos/fallo-http';
import { Proceso } from '../../core/modelos/proceso';
import { ProcesoService } from '../../core/servicios/proceso-service';
import { FormularioProceso } from './formulario-proceso';

const PROCESO: Proceso = {
  id: 7,
  nombre: 'Compras',
  descripcion: 'Gestión de compras',
  categoria: 'Finanzas',
  estado: 'BORRADOR',
  poolId: 80,
  poolNombre: 'Empresa de prueba',
  eliminado: false,
  empresaPropietariaId: 7,
  empresaPropietariaNombre: 'Empresa de prueba',
  soloLectura: false,
};

function fallo(parcial: Partial<FalloHttp>): FalloHttp {
  return { tipo: 'datos-no-validos', estado: 400, codigo: null, titulo: 'Los datos enviados no son válidos', detalle: null, ...parcial };
}

describe('FormularioProceso', () => {
  let fixture: ComponentFixture<FormularioProceso>;
  let elemento: HTMLElement;
  let navegar: MockInstance;
  const servicio = { obtener: vi.fn(), crear: vi.fn(), editar: vi.fn() };

  async function crear(id?: string, proceso: Proceso = PROCESO): Promise<void> {
    servicio.obtener.mockReset().mockReturnValue(of(proceso));
    servicio.crear.mockReset();
    servicio.editar.mockReset();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: ProcesoService, useValue: servicio }],
    });
    navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture = TestBed.createComponent(FormularioProceso);
    elemento = fixture.nativeElement;
    if (id !== undefined) {
      fixture.componentRef.setInput('id', id);
    }
    await fixture.whenStable();
  }

  function escribir(selector: string, valor: string): void {
    const control = elemento.querySelector<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>(selector)!;
    control.value = valor;
    control.dispatchEvent(new Event(control instanceof HTMLSelectElement ? 'change' : 'input'));
  }

  async function enviar(): Promise<void> {
    elemento.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  function errores(): string[] {
    return Array.from(elemento.querySelectorAll('.campo__error'))
      .map((error) => error.textContent?.trim() ?? '')
      .filter((texto) => texto.length > 0);
  }

  function botonGuardar(): HTMLButtonElement {
    return elemento.querySelector<HTMLButtonElement>('button[type="submit"]')!;
  }

  async function completarCreacion(): Promise<void> {
    escribir('#campo-nombre', 'Compras');
    escribir('#campo-categoria', 'Finanzas');
    escribir('#campo-descripcion', 'Gestión de compras');
    await enviar();
  }

  it('en creación exige los campos obligatorios, no ofrece el estado y no llama a la API', async () => {
    await crear();

    expect(elemento.querySelector('h1')?.textContent).toBe('Nuevo proceso');
    expect(elemento.querySelector('#campo-estado')).toBeNull();
    await enviar();

    expect(errores()).toEqual([
      'El nombre es obligatorio',
      'La categoría es obligatoria',
      'La descripción es obligatoria',
    ]);
    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('Revisa los campos marcados');
    expect(servicio.crear).not.toHaveBeenCalled();
  });

  it('aplica las mismas reglas de longitud y de texto en blanco que el backend', async () => {
    await crear();

    escribir('#campo-nombre', 'x'.repeat(151));
    escribir('#campo-categoria', '   ');
    escribir('#campo-descripcion', 'Proceso de prueba');
    await enviar();

    expect(errores()).toEqual(['El nombre no puede superar 150 caracteres', 'La categoría es obligatoria']);
    expect(elemento.textContent).toContain('151 / 150');
    expect(servicio.crear).not.toHaveBeenCalled();
  });

  it('crea el proceso con el contrato de CrearProcesoDto y abre su detalle', async () => {
    await crear();
    servicio.crear.mockReturnValue(of({ ...PROCESO, id: 31 }));

    await completarCreacion();

    expect(servicio.crear).toHaveBeenCalledWith({ nombre: 'Compras', descripcion: 'Gestión de compras', categoria: 'Finanzas' });
    expect(navegar).toHaveBeenCalledWith(['/procesos', 31]);
  });

  it('muestra el nombre duplicado junto al campo y no navega', async () => {
    await crear();
    servicio.crear.mockReturnValue(
      throwError(() =>
        fallo({ tipo: 'conflicto', estado: 409, codigo: 'PROCESO_NOMBRE_DUPLICADO', detalle: 'Ya existe un proceso con ese nombre' }),
      ),
    );

    await completarCreacion();

    expect(errores()).toEqual(['Ya existe un proceso con ese nombre']);
    expect(elemento.querySelector<HTMLInputElement>('#campo-nombre')?.getAttribute('aria-invalid')).toBe('true');
    expect(navegar).not.toHaveBeenCalled();
    expect(botonGuardar().disabled).toBe(false);
  });

  it('asocia a cada campo los errores de validación del backend', async () => {
    await crear();
    servicio.crear.mockReturnValue(
      throwError(() => fallo({ codigo: 'DATOS_NO_VALIDOS', campos: { categoria: 'La categoría es obligatoria' } })),
    );

    await completarCreacion();

    expect(errores()).toEqual(['La categoría es obligatoria']);
  });

  it('informa un rechazo por permisos sin presentarlo como guardado', async () => {
    await crear();
    servicio.crear.mockReturnValue(
      throwError(() =>
        fallo({
          tipo: 'sin-permiso',
          estado: 403,
          codigo: 'USUARIO_SIN_PERMISO',
          titulo: 'No tienes permiso para esta consulta',
          detalle: 'Tu rol no permite crear procesos',
        }),
      ),
    );

    await completarCreacion();

    expect(elemento.textContent).toContain('Tu rol no permite crear procesos');
    expect(navegar).not.toHaveBeenCalled();
  });

  it('deshabilita el envío mientras espera la respuesta', async () => {
    await crear();
    servicio.crear.mockReturnValue(NEVER);

    await completarCreacion();

    expect(botonGuardar().disabled).toBe(true);
    expect(botonGuardar().textContent?.trim()).toBe('Guardando…');
  });

  it('en edición carga el proceso real y habilita el estado', async () => {
    await crear('7');

    expect(servicio.obtener).toHaveBeenCalledWith(7);
    expect(elemento.querySelector('h1')?.textContent).toBe('Editar proceso');
    expect(elemento.querySelector<HTMLInputElement>('#campo-nombre')?.value).toBe('Compras');
    expect(elemento.querySelector<HTMLSelectElement>('#campo-estado')?.value).toBe('BORRADOR');
    expect(botonGuardar().textContent?.trim()).toBe('Guardar cambios');
  });

  it('guarda la edición con todos los campos permitidos y vuelve al detalle', async () => {
    await crear('7');
    servicio.editar.mockReturnValue(of({ ...PROCESO, nombre: 'Compras nacionales', estado: 'PUBLICADO' }));

    escribir('#campo-nombre', 'Compras nacionales');
    escribir('#campo-estado', 'PUBLICADO');
    await enviar();

    expect(servicio.editar).toHaveBeenCalledWith(7, {
      nombre: 'Compras nacionales',
      descripcion: 'Gestión de compras',
      categoria: 'Finanzas',
      estado: 'PUBLICADO',
    });
    expect(navegar).toHaveBeenCalledWith(['/procesos', 7]);
  });

  it('explica cuando el backend impide publicar un modelo incompleto', async () => {
    await crear('7');
    servicio.editar.mockReturnValue(
      throwError(() => fallo({ codigo: 'MODELO_NO_VALIDO', detalle: 'El gateway exclusivo necesita dos salidas' })),
    );

    escribir('#campo-estado', 'PUBLICADO');
    await enviar();

    expect(elemento.textContent).toContain('El proceso todavía no se puede publicar');
    expect(elemento.textContent).toContain('El gateway exclusivo necesita dos salidas');
    expect(navegar).not.toHaveBeenCalled();
  });

  it('bloquea la edición de un proceso compartido en solo lectura', async () => {
    await crear('7', { ...PROCESO, soloLectura: true });

    expect(elemento.textContent).toContain('Este proceso no se puede editar');
    expect(elemento.querySelector<HTMLInputElement>('#campo-nombre')?.disabled).toBe(true);
    expect(botonGuardar().disabled).toBe(true);
  });

  it('bloquea la edición de un proceso eliminado', async () => {
    await crear('7', { ...PROCESO, eliminado: true });

    expect(elemento.textContent).toContain('El proceso fue eliminado.');
    expect(botonGuardar().disabled).toBe(true);
  });
});

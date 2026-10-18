import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';

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

describe('FormularioProceso', () => {
  let fixture: ComponentFixture<FormularioProceso>;
  let elemento: HTMLElement;
  const servicio = { obtener: vi.fn(), crear: vi.fn(), editar: vi.fn() };

  async function crear(id?: string, proceso: Proceso = PROCESO): Promise<void> {
    servicio.obtener.mockReset().mockReturnValue(of(proceso));
    servicio.crear.mockReset();
    servicio.editar.mockReset();
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: ProcesoService, useValue: servicio }],
    });
    fixture = TestBed.createComponent(FormularioProceso);
    elemento = fixture.nativeElement;
    if (id !== undefined) {
      fixture.componentRef.setInput('id', id);
    }
    await fixture.whenStable();
  }

  function escribir(selector: string, valor: string): void {
    const control = elemento.querySelector<HTMLInputElement | HTMLTextAreaElement>(selector)!;
    control.value = valor;
    control.dispatchEvent(new Event('input'));
  }

  async function revisar(): Promise<void> {
    elemento.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  function errores(): string[] {
    return Array.from(elemento.querySelectorAll('.campo__error'))
      .map((error) => error.textContent?.trim() ?? '')
      .filter((texto) => texto.length > 0);
  }

  it('en creación exige los campos obligatorios y no ofrece el estado', async () => {
    await crear();

    expect(elemento.querySelector('h1')?.textContent).toBe('Nuevo proceso');
    expect(elemento.querySelector('#campo-estado')).toBeNull();
    await revisar();

    expect(errores()).toEqual([
      'El nombre es obligatorio',
      'La categoría es obligatoria',
      'La descripción es obligatoria',
    ]);
    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('Revisa los campos marcados');
  });

  it('aplica las mismas reglas de longitud y de texto en blanco que el backend', async () => {
    await crear();

    escribir('#campo-nombre', 'x'.repeat(151));
    escribir('#campo-categoria', '   ');
    escribir('#campo-descripcion', 'Proceso de prueba');
    await revisar();

    expect(errores()).toEqual(['El nombre no puede superar 150 caracteres', 'La categoría es obligatoria']);
    expect(elemento.textContent).toContain('151 / 150');
  });

  it('valida sin guardar ni simular un guardado exitoso', async () => {
    await crear();

    escribir('#campo-nombre', 'Compras');
    escribir('#campo-categoria', 'Finanzas');
    escribir('#campo-descripcion', 'Gestión de compras');
    await revisar();

    expect(errores()).toEqual([]);
    expect(elemento.textContent).toContain('No se ha enviado nada al servidor');
    const guardar = Array.from(elemento.querySelectorAll<HTMLButtonElement>('button')).find(
      (boton) => boton.textContent?.trim() === 'Guardar proceso',
    );
    expect(guardar?.disabled).toBe(true);
    expect(servicio.crear).not.toHaveBeenCalled();
    expect(servicio.editar).not.toHaveBeenCalled();
  });

  it('en edición carga el proceso real y habilita el estado', async () => {
    await crear('7');

    expect(servicio.obtener).toHaveBeenCalledWith(7);
    expect(elemento.querySelector('h1')?.textContent).toBe('Editar proceso');
    expect(elemento.querySelector<HTMLInputElement>('#campo-nombre')?.value).toBe('Compras');
    expect(elemento.querySelector<HTMLSelectElement>('#campo-estado')?.value).toBe('BORRADOR');
    expect(servicio.editar).not.toHaveBeenCalled();
  });

  it('bloquea la edición de un proceso compartido en solo lectura', async () => {
    await crear('7', { ...PROCESO, soloLectura: true });

    expect(elemento.textContent).toContain('Este proceso no se puede editar');
    expect(elemento.querySelector<HTMLInputElement>('#campo-nombre')?.disabled).toBe(true);
  });
});

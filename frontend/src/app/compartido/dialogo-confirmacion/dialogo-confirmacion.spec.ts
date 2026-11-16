import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DialogoConfirmacion } from './dialogo-confirmacion';

describe('DialogoConfirmacion', () => {
  let fixture: ComponentFixture<DialogoConfirmacion>;
  let elemento: HTMLElement;
  let confirmaciones: number;
  let cancelaciones: number;

  async function crear(entradas: Record<string, unknown>): Promise<void> {
    fixture = TestBed.createComponent(DialogoConfirmacion);
    elemento = fixture.nativeElement;
    confirmaciones = 0;
    cancelaciones = 0;
    fixture.componentInstance.confirmado.subscribe(() => confirmaciones++);
    fixture.componentInstance.cancelado.subscribe(() => cancelaciones++);
    fixture.componentRef.setInput('titulo', '¿Eliminar este proceso?');
    Object.entries(entradas).forEach(([nombre, valor]) => fixture.componentRef.setInput(nombre, valor));
    await fixture.whenStable();
  }

  function boton(texto: string): HTMLButtonElement {
    return Array.from(elemento.querySelectorAll<HTMLButtonElement>('button')).find(
      (candidato) => candidato.textContent?.trim() === texto,
    )!;
  }

  it('no muestra nada mientras está cerrado', async () => {
    await crear({ abierto: false });

    expect(elemento.querySelector('[role="alertdialog"]')).toBeNull();
  });

  it('se anuncia como diálogo modal con título, mensaje y el foco en cancelar', async () => {
    await crear({ abierto: true, mensaje: 'Se conserva con su historial.', textoConfirmar: 'Eliminar proceso' });

    const dialogo = elemento.querySelector('[role="alertdialog"]')!;
    expect(dialogo.getAttribute('aria-modal')).toBe('true');
    expect(elemento.querySelector(`#${dialogo.getAttribute('aria-labelledby')}`)?.textContent).toBe(
      '¿Eliminar este proceso?',
    );
    expect(elemento.querySelector(`#${dialogo.getAttribute('aria-describedby')}`)?.textContent).toContain(
      'Se conserva con su historial.',
    );
    expect(document.activeElement).toBe(boton('Cancelar'));
  });

  it('emite la confirmación y la cancelación sin decidir por el componente padre', async () => {
    await crear({ abierto: true, textoConfirmar: 'Eliminar proceso' });

    boton('Eliminar proceso').click();
    boton('Cancelar').click();
    elemento.querySelector('.dialogo__fondo')!.dispatchEvent(new MouseEvent('click'));
    elemento.querySelector('[role="alertdialog"]')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(confirmaciones).toBe(1);
    expect(cancelaciones).toBe(3);
  });

  it('mientras procesa bloquea los botones y no permite cancelar', async () => {
    await crear({ abierto: true, procesando: true });

    expect(boton('Procesando…').disabled).toBe(true);
    expect(boton('Cancelar').disabled).toBe(true);
    elemento.querySelector('[role="alertdialog"]')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));

    expect(cancelaciones).toBe(0);
  });

  it('muestra el error que recibe del componente padre', async () => {
    await crear({ abierto: true, error: 'Solo un administrador puede eliminar procesos' });

    expect(elemento.querySelector('[role="alert"]')?.textContent).toBe('Solo un administrador puede eliminar procesos');
  });
});

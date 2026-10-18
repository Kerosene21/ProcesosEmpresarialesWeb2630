import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MetadatosPagina } from '../../core/modelos/pagina';
import { Paginacion } from './paginacion';

describe('Paginacion', () => {
  let fixture: ComponentFixture<Paginacion>;
  let elemento: HTMLElement;
  let emitidas: number[];

  async function mostrar(pagina: MetadatosPagina): Promise<void> {
    fixture.componentRef.setInput('pagina', pagina);
    await fixture.whenStable();
  }

  function boton(texto: string): HTMLButtonElement {
    return Array.from(elemento.querySelectorAll<HTMLButtonElement>('button')).find((candidato) =>
      candidato.textContent?.includes(texto),
    )!;
  }

  beforeEach(() => {
    fixture = TestBed.createComponent(Paginacion);
    elemento = fixture.nativeElement;
    emitidas = [];
    fixture.componentInstance.cambio.subscribe((pagina) => emitidas.push(pagina));
  });

  it('resume el rango mostrado y marca la página actual', async () => {
    await mostrar({ size: 10, number: 1, totalElements: 25, totalPages: 3 });

    expect(elemento.querySelector('.paginacion__resumen')?.textContent).toContain('11–20');
    expect(elemento.querySelector('[aria-current="page"]')?.textContent?.trim()).toBe('2');
  });

  it('emite el índice de la página elegida para que el padre consulte', async () => {
    await mostrar({ size: 10, number: 0, totalElements: 25, totalPages: 3 });

    elemento.querySelector<HTMLButtonElement>('[aria-label="Página 3"]')!.click();
    boton('Siguiente').click();

    expect(emitidas).toEqual([2, 1]);
  });

  it('desactiva los extremos y no emite fuera de rango', async () => {
    await mostrar({ size: 10, number: 0, totalElements: 25, totalPages: 3 });

    expect(boton('Anterior').disabled).toBe(true);
    boton('Anterior').click();
    expect(emitidas).toEqual([]);
  });

  it('oculta los controles cuando solo hay una página', async () => {
    await mostrar({ size: 10, number: 0, totalElements: 4, totalPages: 1 });

    expect(elemento.querySelectorAll('button').length).toBe(0);
    expect(elemento.textContent).toContain('1–4');
  });
});

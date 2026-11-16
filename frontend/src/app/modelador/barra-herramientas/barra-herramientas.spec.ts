import { ComponentFixture, TestBed } from '@angular/core/testing';

import { BarraHerramientas, ElementoNuevo, Herramienta } from './barra-herramientas';

describe('BarraHerramientas', () => {
  let fixture: ComponentFixture<BarraHerramientas>;
  let elemento: HTMLElement;
  let elegidas: Herramienta[];
  let nuevos: ElementoNuevo[];

  async function crear(herramienta: Herramienta, indicacion: string | null = null): Promise<void> {
    fixture = TestBed.createComponent(BarraHerramientas);
    elemento = fixture.nativeElement;
    elegidas = [];
    nuevos = [];
    fixture.componentInstance.herramientaElegida.subscribe((valor) => elegidas.push(valor));
    fixture.componentInstance.nuevo.subscribe((valor) => nuevos.push(valor));
    fixture.componentRef.setInput('herramienta', herramienta);
    fixture.componentRef.setInput('indicacion', indicacion);
    await fixture.whenStable();
  }

  function boton(texto: string): HTMLButtonElement {
    return Array.from(elemento.querySelectorAll('button')).find((actual) => actual.textContent?.trim() === texto)!;
  }

  it('marca la herramienta activa y explica cómo usarla', async () => {
    await crear('ACTIVIDAD');

    expect(elemento.querySelector('[role="toolbar"]')?.getAttribute('aria-label')).toBe('Herramientas del lienzo');
    expect(boton('Actividad').getAttribute('aria-pressed')).toBe('true');
    expect(boton('Seleccionar').getAttribute('aria-pressed')).toBe('false');
    expect(elemento.querySelector('[role="status"]')?.textContent).toContain('Pulsa dentro de una lane');
  });

  it('prioriza la indicación del modelador sobre la ayuda de la herramienta', async () => {
    await crear('ARCO', 'Origen: «Registrar solicitud». Pulsa el nodo de destino.');

    expect(elemento.querySelector('[role="status"]')?.textContent).toContain('Origen: «Registrar solicitud»');
  });

  it('emite la herramienta elegida y la creación mediante formulario', async () => {
    await crear('SELECCION');

    boton('Arco').click();
    boton('Nuevo gateway').click();

    expect(elegidas).toEqual(['ARCO']);
    expect(nuevos).toEqual(['GATEWAY']);
  });

  it('ofrece las herramientas de eventos de mensaje sin quitar las anteriores', async () => {
    await crear('MESSAGE_THROW');
    const herramientas = Array.from(elemento.querySelectorAll('[role="toolbar"] button')).map((actual) => actual.textContent?.trim());

    expect(herramientas).toEqual(['Seleccionar', 'Actividad', 'Gateway', 'Arco', 'Message Throw', 'Message Catch', 'Envío externo']);
    expect(elemento.querySelector('[role="status"]')?.textContent).toContain('El pool receptor se elige en el formulario');

    boton('Envío externo').click();
    boton('Nuevo Message Catch').click();
    boton('Nuevo envío externo').click();

    expect(elegidas).toEqual(['ENVIO_EXTERNO']);
    expect(nuevos).toEqual(['MESSAGE_CATCH', 'ENVIO_EXTERNO']);
  });
});

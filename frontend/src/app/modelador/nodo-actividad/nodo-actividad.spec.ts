import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { actividad } from '../../../testing/diagramas';
import { ActividadRespuesta } from '../../core/modelos/diagrama';
import { NodoActividad } from './nodo-actividad';

const SVG = 'http://www.w3.org/2000/svg';

@Component({
  imports: [NodoActividad],
  template: `<svg (click)="clicsFondo = clicsFondo + 1">
    <g appNodoActividad [actividad]="actividad()" [seleccionado]="seleccionado()" (elegido)="elegidos = elegidos + 1"></g>
  </svg>`,
})
class Anfitrion {
  readonly actividad = signal<ActividadRespuesta>(actividad(1, 'Registrar solicitud del cliente', 11, 160, 60));
  readonly seleccionado = signal(false);
  elegidos = 0;
  clicsFondo = 0;
}

describe('NodoActividad', () => {
  let fixture: ComponentFixture<Anfitrion>;
  let nodo: SVGGElement;

  beforeEach(async () => {
    fixture = TestBed.createComponent(Anfitrion);
    await fixture.whenStable();
    nodo = fixture.nativeElement.querySelector('g[appNodoActividad]');
  });

  it('dibuja el rectángulo en el espacio de nombres SVG con la posición absoluta del backend', () => {
    const rect = nodo.querySelector('rect')!;

    expect(rect.namespaceURI).toBe(SVG);
    expect(nodo.querySelector('text')!.namespaceURI).toBe(SVG);
    expect([rect.getAttribute('x'), rect.getAttribute('y'), rect.getAttribute('width'), rect.getAttribute('height')]).toEqual([
      '160',
      '60',
      '130',
      '46',
    ]);
  });

  it('recorta el nombre largo y conserva el nombre completo en el título', () => {
    const textos = Array.from(nodo.querySelectorAll('text')).map((texto) => texto.textContent);

    expect(textos).toEqual(['Registrar solici…', 'Tarea de usuario']);
    expect(nodo.querySelector('title')?.textContent).toContain('Registrar solicitud del cliente');
  });

  it('se anuncia como botón accesible y refleja la selección', async () => {
    expect(nodo.getAttribute('role')).toBe('button');
    expect(nodo.getAttribute('tabindex')).toBe('0');
    expect(nodo.getAttribute('aria-label')).toBe('Actividad Registrar solicitud del cliente, Tarea de usuario, lane General');
    expect(nodo.getAttribute('aria-pressed')).toBe('false');

    fixture.componentInstance.seleccionado.set(true);
    await fixture.whenStable();

    expect(nodo.classList).toContain('nodo--seleccionado');
    expect(nodo.getAttribute('aria-pressed')).toBe('true');
  });

  it('emite la elección con clic, Enter y espacio sin propagar el clic al lienzo', () => {
    nodo.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    nodo.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    nodo.dispatchEvent(new KeyboardEvent('keydown', { key: ' ' }));

    expect(fixture.componentInstance.elegidos).toBe(3);
    expect(fixture.componentInstance.clicsFondo).toBe(0);
  });

  it('marca con una alerta las actividades que traen advertencias', async () => {
    expect(nodo.querySelector('.alerta')).toBeNull();

    fixture.componentInstance.actividad.update((actual) => ({ ...actual, advertencias: ['Sin conexiones'] }));
    await fixture.whenStable();

    expect(nodo.querySelector('.alerta')?.getAttribute('cx')).toBe('290');
  });
});

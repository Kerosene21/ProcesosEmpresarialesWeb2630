import { Component } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { diagramaCompleto } from '../../../testing/diagramas';
import { Segmento } from '../geometria';
import { FlujoMensaje } from './flujo-mensaje';

@Component({
  imports: [FlujoMensaje],
  template: `<svg><g appFlujoMensaje [flujo]="flujo" [segmento]="segmento" marcador="mensaje"></g></svg>`,
})
class Anfitrion {
  readonly flujo = diagramaCompleto().flujosMensaje[0];
  readonly segmento: Segmento = { desde: { x: 78, y: 380 }, hasta: { x: 78, y: 106 } };
}

describe('FlujoMensaje', () => {
  let fixture: ComponentFixture<Anfitrion>;
  let flujo: SVGGElement;

  beforeEach(async () => {
    fixture = TestBed.createComponent(Anfitrion);
    await fixture.whenStable();
    flujo = fixture.nativeElement.querySelector('g[appFlujoMensaje]');
  });

  it('dibuja una línea punteada con círculo de origen y flecha hacia el receptor', () => {
    const linea = flujo.querySelector('.flujo__linea')!;

    expect(['x1', 'y1', 'x2', 'y2'].map((atributo) => linea.getAttribute(atributo))).toEqual(['78', '380', '78', '106']);
    expect(linea.getAttribute('marker-end')).toBe('url(#mensaje)');
    expect(flujo.querySelector('.flujo__inicio')?.getAttribute('cy')).toBe('380');
  });

  it('nombra el mensaje y el tipo de evento que lo origina', () => {
    expect(flujo.querySelector('.flujo__texto')?.textContent).toBe('Solicitud de servicio');
    expect(flujo.querySelector('title')?.textContent).toBe('Flujo de mensaje (Message Throw): Solicitud de servicio');
  });
});

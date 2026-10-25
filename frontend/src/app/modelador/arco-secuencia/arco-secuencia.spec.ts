import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { diagramaCompleto } from '../../../testing/diagramas';
import { ArcoRespuesta } from '../../core/modelos/diagrama';
import { Segmento } from '../geometria';
import { ArcoSecuencia } from './arco-secuencia';

const ARCO = diagramaCompleto().arcos.find((arco) => arco.id === 35)!;

@Component({
  imports: [ArcoSecuencia],
  template: `<svg>
    <g appArcoSecuencia [arco]="arco()" [segmento]="segmento" marcador="punta" [seleccionado]="seleccionado()" (elegido)="elegidos = elegidos + 1"></g>
  </svg>`,
})
class Anfitrion {
  readonly arco = signal<ArcoRespuesta>(ARCO);
  readonly segmento: Segmento = { desde: { x: 543, y: 93 }, hasta: { x: 820, y: 88 } };
  readonly seleccionado = signal(false);
  elegidos = 0;
}

describe('ArcoSecuencia', () => {
  let fixture: ComponentFixture<Anfitrion>;
  let arco: SVGGElement;

  beforeEach(async () => {
    fixture = TestBed.createComponent(Anfitrion);
    await fixture.whenStable();
    arco = fixture.nativeElement.querySelector('g[appArcoSecuencia]');
  });

  it('dibuja la línea con los extremos recibidos y una flecha de dirección', () => {
    const linea = arco.querySelector('.arco__linea')!;

    expect(['x1', 'y1', 'x2', 'y2'].map((atributo) => linea.getAttribute(atributo))).toEqual(['543', '93', '820', '88']);
    expect(linea.getAttribute('marker-end')).toBe('url(#punta)');
    expect(arco.querySelector('.arco__zona')).not.toBeNull();
  });

  it('muestra la etiqueta y la condición en el punto medio', () => {
    const textos = Array.from(arco.querySelectorAll('text'));

    expect(textos.map((texto) => texto.textContent)).toEqual(['No procede', '[Rechazada]']);
    expect(textos[0].getAttribute('x')).toBe('682');
    expect(arco.getAttribute('aria-label')).toBe(
      'Arco de Gateway EXCLUSIVO #4 a Envío externo: Notificación de resultado, No procede, condición Rechazada',
    );
  });

  it('usa la flecha resaltada cuando el arco está seleccionado', async () => {
    fixture.componentInstance.seleccionado.set(true);
    await fixture.whenStable();

    expect(arco.querySelector('.arco__linea')?.getAttribute('marker-end')).toBe('url(#punta-seleccion)');
    expect(arco.classList).toContain('arco--seleccionado');
  });

  it('no dibuja textos vacíos y emite la elección con espacio', async () => {
    fixture.componentInstance.arco.set({ ...ARCO, etiqueta: null, condicion: null });
    await fixture.whenStable();
    arco.dispatchEvent(new KeyboardEvent('keydown', { key: ' ' }));

    expect(arco.querySelectorAll('text').length).toBe(0);
    expect(fixture.componentInstance.elegidos).toBe(1);
  });
});

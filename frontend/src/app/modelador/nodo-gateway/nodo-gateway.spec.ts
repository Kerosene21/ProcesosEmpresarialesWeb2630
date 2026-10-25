import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { gateway } from '../../../testing/diagramas';
import { GatewayRespuesta, TipoGateway } from '../../core/modelos/diagrama';
import { NodoGateway } from './nodo-gateway';

@Component({
  imports: [NodoGateway],
  template: `<svg><g appNodoGateway [gateway]="gateway()" [seleccionado]="seleccionado()" (elegido)="elegidos = elegidos + 1"></g></svg>`,
})
class Anfitrion {
  readonly gateway = signal<GatewayRespuesta>(gateway(4, 520, 70));
  readonly seleccionado = signal(false);
  elegidos = 0;
}

describe('NodoGateway', () => {
  let fixture: ComponentFixture<Anfitrion>;
  let nodo: SVGGElement;

  async function crear(tipo: TipoGateway): Promise<void> {
    fixture = TestBed.createComponent(Anfitrion);
    fixture.componentInstance.gateway.set(gateway(4, 520, 70, { tipo }));
    await fixture.whenStable();
    nodo = fixture.nativeElement.querySelector('g[appNodoGateway]');
  }

  it('dibuja el rombo de 46 px desde su esquina absoluta', async () => {
    await crear('EXCLUSIVO');

    expect(nodo.querySelector('polygon')?.getAttribute('points')).toBe('543,70 566,93 543,116 520,93');
    expect(nodo.querySelector('.gateway__tipo')?.textContent).toBe('Exclusivo');
    expect(nodo.getAttribute('aria-label')).toBe('Gateway exclusivo #4');
  });

  it('distingue el exclusivo con una X', async () => {
    await crear('EXCLUSIVO');
    const lineas = Array.from(nodo.querySelectorAll('line.gateway__simbolo--exclusivo'));

    expect(lineas.map((linea) => [linea.getAttribute('x1'), linea.getAttribute('y1'), linea.getAttribute('x2'), linea.getAttribute('y2')])).toEqual([
      ['534', '84', '552', '102'],
      ['552', '84', '534', '102'],
    ]);
    expect(nodo.querySelector('circle.gateway__simbolo')).toBeNull();
  });

  it('distingue el paralelo con una cruz', async () => {
    await crear('PARALELO');
    const lineas = Array.from(nodo.querySelectorAll('line.gateway__simbolo--paralelo'));

    expect(lineas.map((linea) => [linea.getAttribute('x1'), linea.getAttribute('x2')])).toEqual([
      ['543', '543'],
      ['531', '555'],
    ]);
    expect(nodo.querySelector('.gateway__tipo')?.textContent).toBe('Paralelo');
  });

  it('distingue el inclusivo con un círculo', async () => {
    await crear('INCLUSIVO');

    expect(nodo.querySelectorAll('line').length).toBe(0);
    expect(nodo.querySelector('circle.gateway__simbolo--inclusivo')?.getAttribute('r')).toBe('11');
  });

  it('refleja la selección y emite la elección con el teclado', async () => {
    await crear('EXCLUSIVO');
    fixture.componentInstance.seleccionado.set(true);
    await fixture.whenStable();
    nodo.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));

    expect(nodo.classList).toContain('nodo--seleccionado');
    expect(fixture.componentInstance.elegidos).toBe(1);
  });
});

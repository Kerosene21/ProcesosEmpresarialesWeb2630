import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { diagramaCompleto } from '../../../testing/diagramas';
import { EventoDiagrama } from '../geometria';
import { NodoEvento } from './nodo-evento';

const DIAGRAMA = diagramaCompleto();
const CATCH_INICIO: EventoDiagrama = { tipo: 'MESSAGE_CATCH', datos: DIAGRAMA.messageCatches[0] };
const THROW: EventoDiagrama = { tipo: 'MESSAGE_THROW', datos: DIAGRAMA.messageThrows[0] };
const ENVIO: EventoDiagrama = { tipo: 'ENVIO_EXTERNO', datos: DIAGRAMA.enviosExternos[0] };

@Component({
  imports: [NodoEvento],
  template: `<svg><g appNodoEvento [evento]="evento()" (elegido)="elegidos = elegidos + 1"></g></svg>`,
})
class Anfitrion {
  readonly evento = signal<EventoDiagrama>(CATCH_INICIO);
  elegidos = 0;
}

describe('NodoEvento', () => {
  let fixture: ComponentFixture<Anfitrion>;

  async function crear(evento: EventoDiagrama): Promise<SVGGElement> {
    fixture = TestBed.createComponent(Anfitrion);
    fixture.componentInstance.evento.set(evento);
    await fixture.whenStable();
    return fixture.nativeElement.querySelector('g[appNodoEvento]');
  }

  it('dibuja el Message Catch de inicio con un círculo simple y el sobre vacío', async () => {
    const nodo = await crear(CATCH_INICIO);
    const borde = nodo.querySelector('.evento__borde')!;

    expect([borde.getAttribute('cx'), borde.getAttribute('cy'), borde.getAttribute('r')]).toEqual(['78', '88', '18']);
    expect(nodo.querySelector('.evento__interior')).toBeNull();
    expect(nodo.querySelector('.evento__sobre--lleno')).toBeNull();
    expect(nodo.classList).toContain('evento--message-catch');
    expect(nodo.getAttribute('aria-label')).toBe('Message Catch de inicio Solicitud de servicio');
  });

  it('dibuja el Message Throw con doble círculo y el sobre relleno', async () => {
    const nodo = await crear(THROW);

    expect(nodo.querySelector('.evento__interior')?.getAttribute('r')).toBe('14');
    expect(nodo.querySelector('.evento__sobre--lleno')).not.toBeNull();
    expect(nodo.querySelector('.evento__nombre')?.textContent).toBe('Solicitud de servicio');
    expect(nodo.classList).toContain('evento--message-throw');
  });

  it('distingue el envío externo y conserva su etiqueta completa en el título', async () => {
    const nodo = await crear(ENVIO);

    expect(nodo.classList).toContain('evento--envio-externo');
    expect(nodo.classList).toContain('nodo');
    expect(nodo.querySelector('title')?.textContent).toBe('Envío externo: Notificación de resultado');
    expect(nodo.getAttribute('aria-label')).toBe('Envío externo Notificación de resultado');
  });

  it('emite la elección al hacer clic', async () => {
    const nodo = await crear(THROW);

    nodo.dispatchEvent(new MouseEvent('click', { bubbles: true }));

    expect(fixture.componentInstance.elegidos).toBe(1);
  });
});

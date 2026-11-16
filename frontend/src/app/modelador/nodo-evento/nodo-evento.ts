import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { TEXTO_TIPO_ELEMENTO } from '../../compartido/textos';
import { EventoDiagrama, cajaDeNodo, centro, recortar } from '../geometria';

const RADIO_INTERIOR = 14;

@Component({
  selector: 'g[appNodoEvento]',
  templateUrl: './nodo-evento.html',
  styleUrl: './nodo-evento.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'nodo',
    role: 'button',
    tabindex: '0',
    '[class]': "'evento--' + claseTipo()",
    '[class.nodo--seleccionado]': 'seleccionado()',
    '[class.nodo--marcado]': 'marcado()',
    '[attr.aria-label]': 'descripcion()',
    '[attr.aria-pressed]': 'seleccionado()',
    '(click)': 'elegir($event)',
    '(keydown.enter)': 'elegir($event)',
    '(keydown.space)': 'elegir($event)',
  },
})
export class NodoEvento {
  readonly evento = input.required<EventoDiagrama>();
  readonly seleccionado = input(false);
  readonly marcado = input(false);
  readonly elegido = output<void>();

  protected readonly radioInterior = RADIO_INTERIOR;
  protected readonly datos = computed(() => this.evento().datos);
  protected readonly caja = computed(() => cajaDeNodo('EVENTO', this.datos().posicionX, this.datos().posicionY));
  protected readonly centro = computed(() => centro(this.caja()));
  protected readonly radio = computed(() => this.caja().ancho / 2);
  protected readonly claseTipo = computed(() => this.evento().tipo.toLowerCase().replace('_', '-'));
  protected readonly inicio = computed(() => {
    const evento = this.evento();
    return evento.tipo === 'MESSAGE_CATCH' && evento.datos.variante === 'INICIO';
  });
  protected readonly emisor = computed(() => this.evento().tipo !== 'MESSAGE_CATCH');
  protected readonly nombre = computed(() => recortar(this.datos().nombreMensaje, 22));
  protected readonly alerta = computed(() => this.datos().advertencias.length > 0);
  protected readonly sobre = computed(() => {
    const { x, y } = this.centro();
    return {
      x: x - 9,
      y: y - 6,
      solapa: `${x - 9},${y - 6} ${x},${y + 1} ${x + 9},${y - 6}`,
    };
  });
  protected readonly descripcion = computed(() => {
    const variante = this.inicio() ? ' de inicio' : '';
    return `${TEXTO_TIPO_ELEMENTO[this.evento().tipo]}${variante} ${this.datos().nombreMensaje}`;
  });

  protected elegir(evento: Event): void {
    evento.preventDefault();
    evento.stopPropagation();
    this.elegido.emit();
  }
}

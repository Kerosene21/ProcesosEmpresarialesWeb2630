import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { TEXTO_TIPO_GATEWAY } from '../../compartido/textos';
import { GatewayRespuesta } from '../../core/modelos/diagrama';
import { cajaDeNodo, centro, puntosRombo } from '../geometria';

interface Linea {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
}

const BRAZO_EXCLUSIVO = 9;
const BRAZO_PARALELO = 12;

@Component({
  selector: 'g[appNodoGateway]',
  templateUrl: './nodo-gateway.html',
  styleUrl: './nodo-gateway.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'nodo',
    role: 'button',
    tabindex: '0',
    '[class.nodo--seleccionado]': 'seleccionado()',
    '[class.nodo--marcado]': 'marcado()',
    '[attr.aria-label]': 'descripcion()',
    '[attr.aria-pressed]': 'seleccionado()',
    '(click)': 'elegir($event)',
    '(keydown.enter)': 'elegir($event)',
    '(keydown.space)': 'elegir($event)',
  },
})
export class NodoGateway {
  readonly gateway = input.required<GatewayRespuesta>();
  readonly seleccionado = input(false);
  readonly marcado = input(false);
  readonly elegido = output<void>();

  protected readonly caja = computed(() => cajaDeNodo('GATEWAY', this.gateway().posicionX, this.gateway().posicionY));
  protected readonly centro = computed(() => centro(this.caja()));
  protected readonly rombo = computed(() => puntosRombo(this.caja()));
  protected readonly tipo = computed(() => TEXTO_TIPO_GATEWAY[this.gateway().tipo]);
  protected readonly alerta = computed(() => this.gateway().advertencias.length > 0);
  protected readonly descripcion = computed(() => `Gateway ${this.tipo().toLowerCase()} #${this.gateway().id}`);
  protected readonly lineas = computed<Linea[]>(() => {
    const { x, y } = this.centro();
    switch (this.gateway().tipo) {
      case 'EXCLUSIVO':
        return [
          { x1: x - BRAZO_EXCLUSIVO, y1: y - BRAZO_EXCLUSIVO, x2: x + BRAZO_EXCLUSIVO, y2: y + BRAZO_EXCLUSIVO },
          { x1: x + BRAZO_EXCLUSIVO, y1: y - BRAZO_EXCLUSIVO, x2: x - BRAZO_EXCLUSIVO, y2: y + BRAZO_EXCLUSIVO },
        ];
      case 'PARALELO':
        return [
          { x1: x, y1: y - BRAZO_PARALELO, x2: x, y2: y + BRAZO_PARALELO },
          { x1: x - BRAZO_PARALELO, y1: y, x2: x + BRAZO_PARALELO, y2: y },
        ];
      case 'INCLUSIVO':
        return [];
    }
  });

  protected elegir(evento: Event): void {
    evento.preventDefault();
    evento.stopPropagation();
    this.elegido.emit();
  }
}

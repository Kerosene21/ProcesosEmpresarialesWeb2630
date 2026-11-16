import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { TEXTO_TIPO_ACTIVIDAD } from '../../compartido/textos';
import { ActividadRespuesta } from '../../core/modelos/diagrama';
import { cajaDeNodo, recortar } from '../geometria';

@Component({
  selector: 'g[appNodoActividad]',
  templateUrl: './nodo-actividad.html',
  styleUrl: './nodo-actividad.css',
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
export class NodoActividad {
  readonly actividad = input.required<ActividadRespuesta>();
  readonly seleccionado = input(false);
  readonly marcado = input(false);
  readonly elegido = output<void>();

  protected readonly caja = computed(() =>
    cajaDeNodo('ACTIVIDAD', this.actividad().posicionX, this.actividad().posicionY),
  );
  protected readonly nombre = computed(() => recortar(this.actividad().nombre, 17));
  protected readonly tipo = computed(() => TEXTO_TIPO_ACTIVIDAD[this.actividad().tipo]);
  protected readonly alerta = computed(() => this.actividad().advertencias.length > 0);
  protected readonly descripcion = computed(
    () => `Actividad ${this.actividad().nombre}, ${this.tipo()}, lane ${this.actividad().laneNombre}`,
  );

  protected elegir(evento: Event): void {
    evento.preventDefault();
    evento.stopPropagation();
    this.elegido.emit();
  }
}

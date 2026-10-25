import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { ArcoRespuesta } from '../../core/modelos/diagrama';
import { Segmento, puntoMedio, recortar } from '../geometria';

@Component({
  selector: 'g[appArcoSecuencia]',
  templateUrl: './arco-secuencia.html',
  styleUrl: './arco-secuencia.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'arco',
    role: 'button',
    tabindex: '0',
    '[class.arco--seleccionado]': 'seleccionado()',
    '[attr.aria-label]': 'descripcion()',
    '[attr.aria-pressed]': 'seleccionado()',
    '(click)': 'elegir($event)',
    '(keydown.enter)': 'elegir($event)',
    '(keydown.space)': 'elegir($event)',
  },
})
export class ArcoSecuencia {
  readonly arco = input.required<ArcoRespuesta>();
  readonly segmento = input.required<Segmento>();
  readonly marcador = input.required<string>();
  readonly seleccionado = input(false);
  readonly elegido = output<void>();

  protected readonly medio = computed(() => puntoMedio(this.segmento()));
  protected readonly punta = computed(() =>
    this.seleccionado() ? `url(#${this.marcador()}-seleccion)` : `url(#${this.marcador()})`,
  );
  protected readonly etiqueta = computed(() => {
    const etiqueta = this.arco().etiqueta;
    return etiqueta ? recortar(etiqueta, 24) : null;
  });
  protected readonly condicion = computed(() => {
    const condicion = this.arco().condicion;
    return condicion ? `[${recortar(condicion, 22)}]` : null;
  });
  protected readonly descripcion = computed(() => {
    const { origenNombre, destinoNombre, etiqueta, condicion } = this.arco();
    const extras = [etiqueta, condicion ? `condición ${condicion}` : null].filter(Boolean).join(', ');
    return `Arco de ${origenNombre} a ${destinoNombre}${extras ? `, ${extras}` : ''}`;
  });

  protected elegir(evento: Event): void {
    evento.preventDefault();
    evento.stopPropagation();
    this.elegido.emit();
  }
}

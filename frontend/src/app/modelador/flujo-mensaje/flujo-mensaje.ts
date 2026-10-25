import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { TEXTO_TIPO_ELEMENTO } from '../../compartido/textos';
import { FlujoMensaje as FlujoMensajeDiagrama } from '../../core/modelos/diagrama';
import { Segmento, puntoMedio, recortar } from '../geometria';

@Component({
  selector: 'g[appFlujoMensaje]',
  templateUrl: './flujo-mensaje.html',
  styleUrl: './flujo-mensaje.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'flujo',
  },
})
export class FlujoMensaje {
  readonly flujo = input.required<FlujoMensajeDiagrama>();
  readonly segmento = input.required<Segmento>();
  readonly marcador = input.required<string>();

  protected readonly medio = computed(() => puntoMedio(this.segmento()));
  protected readonly punta = computed(() => `url(#${this.marcador()})`);
  protected readonly nombre = computed(() => recortar(this.flujo().nombreMensaje, 24));
  protected readonly titulo = computed(
    () => `Flujo de mensaje (${TEXTO_TIPO_ELEMENTO[this.flujo().tipoEvento]}): ${this.flujo().nombreMensaje}`,
  );
}

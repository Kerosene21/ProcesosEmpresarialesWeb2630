import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { EstadoProceso } from '../../core/modelos/proceso';
import { TEXTO_ESTADO } from '../textos';

@Component({
  selector: 'app-etiqueta-estado',
  templateUrl: './etiqueta-estado.html',
  styleUrl: './etiqueta-estado.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EtiquetaEstado {
  readonly estado = input.required<EstadoProceso>();

  protected readonly texto = computed(() => TEXTO_ESTADO[this.estado()]);
  protected readonly publicado = computed(() => this.estado() === 'PUBLICADO');
}

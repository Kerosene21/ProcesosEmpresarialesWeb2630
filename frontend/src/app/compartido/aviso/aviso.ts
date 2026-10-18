import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

export type TipoAviso = 'carga' | 'vacio' | 'error' | 'informacion';

const ICONOS: Record<TipoAviso, string> = {
  carga: '',
  vacio: '○',
  error: '!',
  informacion: 'i',
};

@Component({
  selector: 'app-aviso',
  templateUrl: './aviso.html',
  styleUrl: './aviso.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Aviso {
  readonly tipo = input<TipoAviso>('informacion');
  readonly titulo = input.required<string>();
  readonly detalle = input<string | null>(null);
  readonly accion = input<string | null>(null);
  readonly accionSolicitada = output<void>();

  protected readonly rol = computed(() => (this.tipo() === 'error' ? 'alert' : 'status'));
  protected readonly icono = computed(() => ICONOS[this.tipo()]);
}

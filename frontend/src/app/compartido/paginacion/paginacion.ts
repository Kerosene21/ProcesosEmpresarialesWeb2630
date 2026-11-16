import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { MetadatosPagina } from '../../core/modelos/pagina';

const PAGINAS_VISIBLES = 5;

@Component({
  selector: 'app-paginacion',
  templateUrl: './paginacion.html',
  styleUrl: './paginacion.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Paginacion {
  readonly pagina = input.required<MetadatosPagina>();
  readonly etiqueta = input('Paginación');
  readonly cambio = output<number>();

  protected readonly actual = computed(() => this.pagina().number);
  protected readonly total = computed(() => this.pagina().totalPages);
  protected readonly desde = computed(() =>
    this.pagina().totalElements === 0 ? 0 : this.actual() * this.pagina().size + 1,
  );
  protected readonly hasta = computed(() =>
    Math.min((this.actual() + 1) * this.pagina().size, this.pagina().totalElements),
  );
  protected readonly indices = computed(() => {
    const inicio = Math.max(0, Math.min(this.actual() - 2, this.total() - PAGINAS_VISIBLES));
    const fin = Math.min(this.total(), inicio + PAGINAS_VISIBLES);
    return Array.from({ length: fin - inicio }, (_, posicion) => inicio + posicion);
  });

  protected ir(indice: number): void {
    if (indice >= 0 && indice < this.total() && indice !== this.actual()) {
      this.cambio.emit(indice);
    }
  }
}

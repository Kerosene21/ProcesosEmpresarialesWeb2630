import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Observable, of, switchMap } from 'rxjs';

import { Aviso } from '../../compartido/aviso/aviso';
import { TEXTO_ESTADO, explicarFallo } from '../../compartido/textos';
import { CARGANDO, EstadoCarga } from '../../core/modelos/estado-carga';
import { Historial } from '../../core/modelos/historial';
import { EstadoProceso, Proceso } from '../../core/modelos/proceso';
import { conEstadoCarga } from '../../core/rxjs/con-estado-carga';
import { ProcesoService } from '../../core/servicios/proceso-service';

const IDENTIFICADOR_NO_VALIDO: EstadoCarga<never> = {
  tipo: 'error',
  fallo: {
    tipo: 'no-encontrado',
    estado: 0,
    codigo: null,
    titulo: 'El identificador del proceso no es válido',
    detalle: null,
  },
};

@Component({
  selector: 'app-historial-proceso',
  imports: [DatePipe, RouterLink, Aviso],
  templateUrl: './historial-proceso.html',
  styleUrl: './historial-proceso.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HistorialProceso {
  private readonly procesos = inject(ProcesoService);

  readonly id = input.required<string>();

  protected readonly explicar = explicarFallo;
  private readonly intento = signal(0);
  private readonly solicitud$ = toObservable(computed(() => ({ id: Number(this.id()), intento: this.intento() })));

  protected readonly estadoProceso = toSignal(
    this.solicitud$.pipe(switchMap(({ id }) => this.cargar(id, (valido) => this.procesos.obtener(valido)))),
    { initialValue: CARGANDO as EstadoCarga<Proceso> },
  );
  protected readonly estadoHistorial = toSignal(
    this.solicitud$.pipe(switchMap(({ id }) => this.cargar(id, (valido) => this.procesos.historial(valido)))),
    { initialValue: CARGANDO as EstadoCarga<Historial[]> },
  );

  protected readonly proceso = computed(() => {
    const estado = this.estadoProceso();
    return estado.tipo === 'listo' ? estado.datos : null;
  });
  protected readonly entradas = computed(() => {
    const estado = this.estadoHistorial();
    return estado.tipo === 'listo' ? estado.datos : null;
  });
  protected readonly fallo = computed(() => {
    const estado = this.estadoHistorial();
    return estado.tipo === 'error' ? estado.fallo : null;
  });

  protected estadoAnterior(valor: string | null): string {
    if (!valor) {
      return 'Sin estado previo';
    }
    return TEXTO_ESTADO[valor as EstadoProceso] ?? valor;
  }

  protected reintentar(): void {
    this.intento.update((intento) => intento + 1);
  }

  private cargar<T>(id: number, consulta: (id: number) => Observable<T>): Observable<EstadoCarga<T>> {
    return Number.isInteger(id) && id > 0 ? consulta(id).pipe(conEstadoCarga()) : of(IDENTIFICADOR_NO_VALIDO);
  }
}

import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Observable, of, switchMap } from 'rxjs';

import { Aviso } from '../../compartido/aviso/aviso';
import { EtiquetaEstado } from '../../compartido/etiqueta-estado/etiqueta-estado';
import { TEXTO_ESTADO, TEXTO_TIPO_ACTIVIDAD, TEXTO_TIPO_POOL, explicarFallo } from '../../compartido/textos';
import { DiagramaProceso } from '../../core/modelos/diagrama';
import { CARGANDO, EstadoCarga } from '../../core/modelos/estado-carga';
import { Proceso } from '../../core/modelos/proceso';
import { conEstadoCarga } from '../../core/rxjs/con-estado-carga';
import { ProcesoService } from '../../core/servicios/proceso-service';

interface Metrica {
  etiqueta: string;
  valor: number;
}

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
  selector: 'app-detalle-proceso',
  imports: [RouterLink, Aviso, EtiquetaEstado],
  templateUrl: './detalle-proceso.html',
  styleUrl: './detalle-proceso.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetalleProceso {
  private readonly procesos = inject(ProcesoService);

  readonly id = input.required<string>();

  protected readonly textoEstado = TEXTO_ESTADO;
  private readonly intento = signal(0);
  private readonly solicitud$ = toObservable(computed(() => ({ id: Number(this.id()), intento: this.intento() })));

  protected readonly estadoProceso = toSignal(
    this.solicitud$.pipe(switchMap(({ id }) => this.cargar(id, (valido) => this.procesos.obtener(valido)))),
    { initialValue: CARGANDO as EstadoCarga<Proceso> },
  );
  protected readonly estadoDiagrama = toSignal(
    this.solicitud$.pipe(switchMap(({ id }) => this.cargar(id, (valido) => this.procesos.obtenerDiagrama(valido)))),
    { initialValue: CARGANDO as EstadoCarga<DiagramaProceso> },
  );

  protected readonly proceso = computed(() => datosDe(this.estadoProceso()));
  protected readonly falloProceso = computed(() => falloDe(this.estadoProceso()));
  protected readonly diagrama = computed(() => datosDe(this.estadoDiagrama()));
  protected readonly falloDiagrama = computed(() => falloDe(this.estadoDiagrama()));
  protected readonly explicar = explicarFallo;

  protected readonly puedeEditar = computed(() => {
    const proceso = this.proceso();
    return proceso !== null && !proceso.soloLectura && !proceso.eliminado;
  });

  protected readonly metricas = computed<Metrica[]>(() => {
    const diagrama = this.diagrama();
    if (!diagrama) {
      return [];
    }
    return [
      { etiqueta: 'Pools', valor: diagrama.pools.length },
      { etiqueta: 'Lanes', valor: diagrama.lanes.length },
      { etiqueta: 'Actividades', valor: diagrama.actividades.length },
      { etiqueta: 'Gateways', valor: diagrama.gateways.length },
      { etiqueta: 'Arcos', valor: diagrama.arcos.length },
      { etiqueta: 'Message Throw', valor: diagrama.messageThrows.length },
      { etiqueta: 'Message Catch', valor: diagrama.messageCatches.length },
      { etiqueta: 'Envíos externos', valor: diagrama.enviosExternos.length },
      { etiqueta: 'Flujos de mensaje', valor: diagrama.flujosMensaje.length },
    ];
  });

  protected readonly pools = computed(() => {
    const diagrama = this.diagrama();
    if (!diagrama) {
      return [];
    }
    return diagrama.pools.map((pool) => ({
      id: pool.id,
      nombre: pool.nombre,
      tipo: TEXTO_TIPO_POOL[pool.tipo],
      cajaNegra: pool.cajaNegra,
      empresa: pool.empresaNombre,
      lanes: diagrama.lanes
        .filter((lane) => lane.poolId === pool.id)
        .map((lane) => ({
          id: lane.id,
          nombre: lane.nombre,
          actividades: diagrama.actividades.filter((actividad) => actividad.laneId === lane.id).length,
        })),
    }));
  });

  protected readonly actividades = computed(() => {
    const diagrama = this.diagrama();
    if (!diagrama) {
      return [];
    }
    const nombresPool = new Map(diagrama.pools.map((pool) => [pool.id, pool.nombre]));
    return diagrama.actividades.map((actividad) => ({
      id: actividad.id,
      nombre: actividad.nombre,
      tipo: TEXTO_TIPO_ACTIVIDAD[actividad.tipo],
      lane: actividad.laneNombre,
      pool: nombresPool.get(actividad.poolId) ?? '',
    }));
  });

  protected reintentar(): void {
    this.intento.update((intento) => intento + 1);
  }

  private cargar<T>(id: number, consulta: (id: number) => Observable<T>): Observable<EstadoCarga<T>> {
    return Number.isInteger(id) && id > 0 ? consulta(id).pipe(conEstadoCarga()) : of(IDENTIFICADOR_NO_VALIDO);
  }
}

function datosDe<T>(estado: EstadoCarga<T>): T | null {
  return estado.tipo === 'listo' ? estado.datos : null;
}

function falloDe<T>(estado: EstadoCarga<T>) {
  return estado.tipo === 'error' ? estado.fallo : null;
}

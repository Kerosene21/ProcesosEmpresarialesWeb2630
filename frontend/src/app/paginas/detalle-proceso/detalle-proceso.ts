import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Observable, of, switchMap } from 'rxjs';

import { Aviso } from '../../compartido/aviso/aviso';
import { DialogoConfirmacion } from '../../compartido/dialogo-confirmacion/dialogo-confirmacion';
import { EtiquetaEstado } from '../../compartido/etiqueta-estado/etiqueta-estado';
import { TEXTO_ESTADO, explicarFallo } from '../../compartido/textos';
import { DiagramaProceso } from '../../core/modelos/diagrama';
import { CARGANDO, EstadoCarga } from '../../core/modelos/estado-carga';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { Proceso } from '../../core/modelos/proceso';
import { ROLES_ESCRITURA } from '../../core/modelos/sesion';
import { conEstadoCarga } from '../../core/rxjs/con-estado-carga';
import { ProcesoService } from '../../core/servicios/proceso-service';
import { SesionService } from '../../core/servicios/sesion-service';
import { PanelComparticion } from '../../configuracion-proceso/panel-comparticion/panel-comparticion';
import { PanelPermisos } from '../../configuracion-proceso/panel-permisos/panel-permisos';
import { Modelador } from '../../modelador/modelador/modelador';

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
  imports: [RouterLink, Aviso, DialogoConfirmacion, EtiquetaEstado, Modelador, PanelComparticion, PanelPermisos],
  templateUrl: './detalle-proceso.html',
  styleUrl: './detalle-proceso.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetalleProceso {
  private readonly procesos = inject(ProcesoService);
  private readonly sesion = inject(SesionService);

  readonly id = input.required<string>();

  protected readonly confirmandoEliminacion = signal(false);
  protected readonly eliminando = signal(false);
  protected readonly falloEliminacion = signal<string | null>(null);
  protected readonly eliminacionConfirmada = signal(false);

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

  protected readonly modificable = computed(() => {
    const proceso = this.proceso();
    return proceso !== null && !proceso.soloLectura && !proceso.eliminado;
  });
  protected readonly puedeEditar = computed(() => this.modificable() && this.sesion.tieneRol(ROLES_ESCRITURA));
  protected readonly puedeEliminar = computed(() => this.modificable() && this.sesion.tieneRol(['ADMINISTRADOR']));
  protected readonly puedeVerHistorial = computed(() => this.proceso()?.soloLectura === false);

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

  protected reintentar(): void {
    this.intento.update((intento) => intento + 1);
  }

  protected solicitarEliminacion(): void {
    this.falloEliminacion.set(null);
    this.confirmandoEliminacion.set(true);
  }

  protected cancelarEliminacion(): void {
    this.confirmandoEliminacion.set(false);
    this.falloEliminacion.set(null);
  }

  protected eliminar(): void {
    const proceso = this.proceso();
    if (!proceso) {
      return;
    }
    this.eliminando.set(true);
    this.falloEliminacion.set(null);
    this.procesos.eliminar(proceso.id).subscribe({
      next: () => {
        this.eliminando.set(false);
        this.confirmandoEliminacion.set(false);
        this.eliminacionConfirmada.set(true);
        this.reintentar();
      },
      error: (fallo: FalloHttp) => {
        this.eliminando.set(false);
        this.falloEliminacion.set(fallo.detalle ?? fallo.titulo);
      },
    });
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

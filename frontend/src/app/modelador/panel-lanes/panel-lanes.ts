import { ChangeDetectionStrategy, Component, computed, inject, input, linkedSignal, output, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable, of, switchMap } from 'rxjs';

import { Aviso } from '../../compartido/aviso/aviso';
import { DialogoConfirmacion } from '../../compartido/dialogo-confirmacion/dialogo-confirmacion';
import { aplicarErroresDelServidor, errorDelServidor } from '../../core/formularios/errores-servidor';
import { DiagramaProceso, LaneRespuesta, PoolRespuesta } from '../../core/modelos/diagrama';
import { CARGANDO, EstadoCarga } from '../../core/modelos/estado-carga';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { PosicionNodo } from '../../core/modelos/movimiento';
import { PermisoEstructura } from '../../core/modelos/permiso-estructura';
import { RolProceso } from '../../core/modelos/rol-proceso';
import { conEstadoCarga } from '../../core/rxjs/con-estado-carga';
import { LaneService } from '../../core/servicios/lane-service';
import { ProcesoService } from '../../core/servicios/proceso-service';
import { SesionService } from '../../core/servicios/sesion-service';
import {
  conLaneNueva,
  conLanesOrdenadas,
  recolocarContenido,
  resumenRecolocacion,
  seguidoDeRecolocacion,
  sinLane,
} from '../recolocacion';

export type PermisosLanes = Pick<PermisoEstructura, 'crearLane' | 'editarLane' | 'eliminarLane'>;

const CAMPOS_POR_CODIGO: Readonly<Record<string, string>> = {
  LANE_DUPLICADA: 'rolProcesoId',
  LANE_NO_VALIDA: 'orden',
};

const SIN_ROLES: EstadoCarga<RolProceso[]> = { tipo: 'listo', datos: [] };

@Component({
  selector: 'app-panel-lanes',
  imports: [ReactiveFormsModule, RouterLink, Aviso, DialogoConfirmacion],
  templateUrl: './panel-lanes.html',
  styleUrl: './panel-lanes.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PanelLanes {
  private readonly lanesApi = inject(LaneService);
  private readonly procesos = inject(ProcesoService);
  private readonly sesion = inject(SesionService);

  readonly procesoId = input.required<number>();
  readonly diagrama = input.required<DiagramaProceso>();
  readonly permisos = input.required<PermisosLanes>();
  readonly cambio = output<void>();
  readonly rechazo = output<void>();

  protected readonly esAdministrador = computed(() => this.sesion.tieneRol(['ADMINISTRADOR']));
  protected readonly pools = computed(() =>
    [...this.diagrama().pools].filter((pool) => !pool.cajaNegra).sort((uno, otro) => uno.orden - otro.orden),
  );
  protected readonly poolId = linkedSignal<PoolRespuesta[], number | null>({
    source: this.pools,
    computation: (pools, previo) =>
      previo && pools.some((pool) => pool.id === previo.value)
        ? previo.value
        : (pools.find((pool) => pool.tipo === 'PROPIETARIO')?.id ?? pools[0]?.id ?? null),
  });
  protected readonly lanes = computed(() =>
    this.diagrama()
      .lanes.filter((lane) => lane.poolId === this.poolId())
      .sort((una, otra) => una.orden - otra.orden || una.id - otra.id),
  );
  private readonly actividadesPorLane = computed(() => {
    const conteo = new Map<number, number>();
    this.diagrama().actividades.forEach((actividad) =>
      conteo.set(actividad.laneId, (conteo.get(actividad.laneId) ?? 0) + 1),
    );
    return conteo;
  });
  protected readonly puedeModificar = computed(() => {
    const permisos = this.permisos();
    return permisos.crearLane || permisos.editarLane || permisos.eliminarLane;
  });

  protected readonly estadoRoles = toSignal(
    toObservable(computed(() => ({ procesoId: this.procesoId(), poolId: this.poolId(), necesita: this.necesitaRoles() }))).pipe(
      switchMap(({ procesoId, poolId, necesita }) => this.cargarRoles(procesoId, poolId, necesita)),
    ),
    { initialValue: CARGANDO as EstadoCarga<RolProceso[]> },
  );
  protected readonly roles = computed(() => {
    const estado = this.estadoRoles();
    return estado.tipo === 'listo' ? estado.datos : [];
  });
  protected readonly falloRoles = computed(() => {
    const estado = this.estadoRoles();
    return estado.tipo === 'error' ? estado.fallo : null;
  });
  protected readonly rolesLibres = computed(() => this.rolesSinLane(null));

  protected readonly creacion = new FormGroup({
    rolProcesoId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    orden: new FormControl<number | null>(null, { validators: [Validators.min(1)] }),
  });
  protected readonly edicion = new FormGroup({
    rolProcesoId: new FormControl<number | null>(null, { validators: [Validators.required] }),
  });

  protected readonly editando = signal<number | null>(null);
  protected readonly procesando = signal(false);
  protected readonly fallo = signal<FalloHttp | null>(null);
  protected readonly mensaje = signal<string | null>(null);
  protected readonly porEliminar = signal<LaneRespuesta | null>(null);
  protected readonly falloEliminacion = signal<string | null>(null);
  protected readonly falloRecolocacion = signal<string | null>(null);
  private readonly recolocados = signal(0);

  protected actividades(lane: LaneRespuesta): number {
    return this.actividadesPorLane().get(lane.id) ?? 0;
  }

  protected rolesParaEditar(lane: LaneRespuesta): RolProceso[] {
    return this.rolesSinLane(lane.id);
  }

  protected elegirPool(evento: Event): void {
    this.poolId.set(Number((evento.target as HTMLSelectElement).value));
    this.cancelarEdicion();
    this.limpiarMensajes();
  }

  protected crear(): void {
    const poolId = this.poolId();
    this.limpiarMensajes();
    const { rolProcesoId, orden } = this.creacion.getRawValue();
    if (this.creacion.invalid || poolId === null || rolProcesoId === null) {
      this.creacion.markAllAsTouched();
      return;
    }
    const nodos = recolocarContenido(this.diagrama(), conLaneNueva(this.diagrama(), poolId, orden));
    this.ejecutar(
      this.conRecolocacion(this.lanesApi.crear(this.procesoId(), poolId, { rolProcesoId, orden }), nodos),
      'La lane se creó y el diagrama se actualizó con los datos del servidor.',
      this.creacion,
      () => this.creacion.reset(),
    );
  }

  protected iniciarEdicion(lane: LaneRespuesta): void {
    this.limpiarMensajes();
    this.editando.set(lane.id);
    this.edicion.reset({ rolProcesoId: lane.rolProcesoId });
  }

  protected cancelarEdicion(): void {
    this.editando.set(null);
    this.edicion.reset();
  }

  protected guardarEdicion(lane: LaneRespuesta): void {
    const poolId = this.poolId();
    this.limpiarMensajes();
    const { rolProcesoId } = this.edicion.getRawValue();
    if (this.edicion.invalid || poolId === null || rolProcesoId === null) {
      this.edicion.markAllAsTouched();
      return;
    }
    this.ejecutar(
      this.lanesApi.editar(this.procesoId(), poolId, lane.id, { rolProcesoId, orden: null }),
      'El rol de la lane se actualizó.',
      this.edicion,
      () => this.cancelarEdicion(),
    );
  }

  protected mover(lane: LaneRespuesta, desplazamiento: -1 | 1): void {
    const poolId = this.poolId();
    const ids = this.lanes().map((actual) => actual.id);
    const posicion = ids.indexOf(lane.id);
    const destino = posicion + desplazamiento;
    if (poolId === null || posicion < 0 || destino < 0 || destino >= ids.length) {
      return;
    }
    [ids[posicion], ids[destino]] = [ids[destino], ids[posicion]];
    this.limpiarMensajes();
    const nodos = recolocarContenido(this.diagrama(), conLanesOrdenadas(this.diagrama(), poolId, ids));
    this.ejecutar(
      this.conRecolocacion(this.lanesApi.reordenar(this.procesoId(), poolId, { lanes: ids }), nodos),
      `La lane «${lane.nombre}» cambió de posición.`,
    );
  }

  protected solicitarEliminacion(lane: LaneRespuesta): void {
    this.limpiarMensajes();
    this.falloEliminacion.set(null);
    this.porEliminar.set(lane);
  }

  protected cancelarEliminacion(): void {
    this.porEliminar.set(null);
    this.falloEliminacion.set(null);
  }

  protected eliminar(): void {
    const lane = this.porEliminar();
    const poolId = this.poolId();
    if (!lane || poolId === null) {
      return;
    }
    this.procesando.set(true);
    this.falloEliminacion.set(null);
    const nodos = recolocarContenido(this.diagrama(), sinLane(this.diagrama(), lane.id));
    this.conRecolocacion(this.lanesApi.eliminar(this.procesoId(), poolId, lane.id), nodos).subscribe({
      next: () => {
        this.procesando.set(false);
        this.porEliminar.set(null);
        this.mensaje.set(`La lane «${lane.nombre}» se eliminó.${this.resumenRecolocacion()}`);
        this.cambio.emit();
      },
      error: (fallo: FalloHttp) => {
        this.procesando.set(false);
        this.falloEliminacion.set(fallo.detalle ?? fallo.titulo);
        this.avisarRechazo(fallo);
      },
    });
  }

  protected errorDe(formulario: FormGroup, campo: string): string | null {
    const control = formulario.get(campo);
    if (!control || control.valid || !control.touched) {
      return null;
    }
    const servidor = errorDelServidor(control);
    if (servidor) {
      return servidor;
    }
    if (control.hasError('min')) {
      return 'La posición empieza en 1';
    }
    return 'Elige un rol de proceso';
  }

  private ejecutar(
    solicitud: Observable<unknown>,
    exito: string,
    formulario?: FormGroup,
    alTerminar?: () => void,
  ): void {
    this.procesando.set(true);
    solicitud.subscribe({
      next: () => {
        this.procesando.set(false);
        this.mensaje.set(`${exito}${this.resumenRecolocacion()}`);
        alTerminar?.();
        this.cambio.emit();
      },
      error: (fallo: FalloHttp) => {
        this.procesando.set(false);
        if (!formulario || !aplicarErroresDelServidor(formulario, fallo, CAMPOS_POR_CODIGO)) {
          this.fallo.set(fallo);
        }
        this.avisarRechazo(fallo);
      },
    });
  }

  private avisarRechazo(fallo: FalloHttp): void {
    if (fallo.tipo === 'sin-permiso') {
      this.rechazo.emit();
    }
  }

  private limpiarMensajes(): void {
    this.fallo.set(null);
    this.mensaje.set(null);
    this.falloRecolocacion.set(null);
    this.recolocados.set(0);
  }

  private conRecolocacion<T>(operacion: Observable<T>, nodos: PosicionNodo[]): Observable<T> {
    return seguidoDeRecolocacion(
      operacion,
      nodos,
      (pendientes) => this.procesos.recolocarNodos(this.procesoId(), { nodos: pendientes }),
      (fallo) => (fallo ? this.falloRecolocacion.set(fallo.detalle ?? fallo.titulo) : this.recolocados.set(nodos.length)),
    );
  }

  private resumenRecolocacion(): string {
    return resumenRecolocacion(this.recolocados(), 'para conservar su lane');
  }

  private necesitaRoles(): boolean {
    const permisos = this.permisos();
    return permisos.crearLane || permisos.editarLane;
  }

  private rolesSinLane(laneActual: number | null): RolProceso[] {
    const ocupados = new Set(
      this.lanes()
        .filter((lane) => lane.id !== laneActual && lane.rolProcesoId !== null)
        .map((lane) => lane.rolProcesoId),
    );
    return this.roles().filter((rol) => !ocupados.has(rol.id));
  }

  private cargarRoles(procesoId: number, poolId: number | null, necesita: boolean): Observable<EstadoCarga<RolProceso[]>> {
    return poolId !== null && necesita ? this.lanesApi.rolesDisponibles(procesoId, poolId).pipe(conEstadoCarga()) : of(SIN_ROLES);
  }
}

import { ChangeDetectionStrategy, Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Observable, Subject, map, of, switchMap } from 'rxjs';

import { Aviso } from '../../compartido/aviso/aviso';
import { EtiquetaEstado } from '../../compartido/etiqueta-estado/etiqueta-estado';
import { explicarFallo } from '../../compartido/textos';
import { DiagramaProceso } from '../../core/modelos/diagrama';
import { CARGANDO, EstadoCarga } from '../../core/modelos/estado-carga';
import { FalloHttp } from '../../core/modelos/fallo-http';
import {
  ConfigurarPermisoEstructura,
  PermisoEstructura,
  SIN_PERMISOS_ESTRUCTURA,
  operacionesDe,
} from '../../core/modelos/permiso-estructura';
import { Proceso } from '../../core/modelos/proceso';
import { ROLES_ESCRITURA, RolUsuario } from '../../core/modelos/sesion';
import { conEstadoCarga } from '../../core/rxjs/con-estado-carga';
import { ProcesoService } from '../../core/servicios/proceso-service';
import { SesionService } from '../../core/servicios/sesion-service';
import { Modelador, PermisosEdicion } from '../../modelador/modelador/modelador';
import { PanelLanes } from '../../modelador/panel-lanes/panel-lanes';
import { PanelPools } from '../../modelador/panel-pools/panel-pools';

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

const SIN_PERMISOS: EstadoCarga<ConfigurarPermisoEstructura> = { tipo: 'listo', datos: SIN_PERMISOS_ESTRUCTURA };

@Component({
  selector: 'app-modelador-proceso',
  imports: [RouterLink, Aviso, EtiquetaEstado, Modelador, PanelLanes, PanelPools],
  templateUrl: './modelador-proceso.html',
  styleUrl: './modelador-proceso.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ModeladorProceso {
  private readonly procesos = inject(ProcesoService);
  private readonly sesion = inject(SesionService);

  readonly id = input.required<string>();

  protected readonly explicar = explicarFallo;
  private readonly intento = signal(0);
  private readonly solicitud$ = toObservable(computed(() => ({ id: Number(this.id()), intento: this.intento() })));
  private readonly refrescos = new Subject<number>();

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
  protected readonly diagrama = linkedSignal(() => datosDe(this.estadoDiagrama()));
  protected readonly falloDiagrama = computed(() => falloDe(this.estadoDiagrama()));
  protected readonly modificable = computed(() => {
    const proceso = this.proceso();
    return proceso !== null && !proceso.soloLectura && !proceso.eliminado;
  });
  protected readonly edicion = computed<PermisosEdicion | null>(() =>
    this.modificable()
      ? { escribir: this.sesion.tieneRol(ROLES_ESCRITURA), eliminar: this.sesion.tieneRol(['ADMINISTRADOR']) }
      : null,
  );

  private readonly consultaPermisos = signal(0);
  protected readonly estadoPermisos = toSignal(
    toObservable(
      computed(() => ({
        id: this.modificable() ? (this.proceso()?.id ?? null) : null,
        consulta: this.consultaPermisos(),
      })),
    ).pipe(switchMap(({ id }) => this.cargarPermisos(id))),
    { initialValue: CARGANDO as EstadoCarga<ConfigurarPermisoEstructura> },
  );
  private readonly vigentes = linkedSignal<EstadoCarga<ConfigurarPermisoEstructura>, ConfigurarPermisoEstructura | null>({
    source: this.estadoPermisos,
    computation: (estado, previo) =>
      estado.tipo === 'listo' ? estado.datos : estado.tipo === 'cargando' ? (previo?.value ?? null) : null,
  });
  protected readonly permisos = computed(() => this.vigentes() ?? SIN_PERMISOS_ESTRUCTURA);
  protected readonly consultandoPermisos = computed(
    () => this.vigentes() === null && this.estadoPermisos().tipo === 'cargando',
  );
  protected readonly falloPermisos = computed(() => falloDe(this.estadoPermisos()));

  protected readonly actualizando = signal(false);
  protected readonly falloActualizacion = signal<FalloHttp | null>(null);

  constructor() {
    this.refrescos
      .pipe(
        switchMap((id) => this.procesos.obtenerDiagrama(id).pipe(conEstadoCarga())),
        takeUntilDestroyed(),
      )
      .subscribe((estado) => {
        this.actualizando.set(estado.tipo === 'cargando');
        if (estado.tipo === 'listo') {
          this.diagrama.set(estado.datos);
          this.falloActualizacion.set(null);
        } else if (estado.tipo === 'error') {
          this.falloActualizacion.set(estado.fallo);
        }
      });
  }

  protected refrescarDiagrama(): void {
    const proceso = this.proceso();
    if (proceso) {
      this.refrescos.next(proceso.id);
    }
  }

  protected cambioDeEstructura(): void {
    this.refrescarPermisos();
    this.refrescarDiagrama();
  }

  protected refrescarPermisos(): void {
    this.consultaPermisos.update((consulta) => consulta + 1);
  }

  protected reintentar(): void {
    this.intento.update((intento) => intento + 1);
  }

  private cargarPermisos(id: number | null): Observable<EstadoCarga<ConfigurarPermisoEstructura>> {
    const rol = this.sesion.rol();
    if (id === null || rol === null) {
      return of(SIN_PERMISOS);
    }
    return this.procesos.permisosEstructura(id).pipe(
      map((matriz) => permisosDelRol(matriz, rol)),
      conEstadoCarga(),
    );
  }

  private cargar<T>(id: number, consulta: (id: number) => Observable<T>): Observable<EstadoCarga<T>> {
    return Number.isInteger(id) && id > 0 ? consulta(id).pipe(conEstadoCarga()) : of(IDENTIFICADOR_NO_VALIDO);
  }
}

function permisosDelRol(matriz: PermisoEstructura[], rol: RolUsuario): ConfigurarPermisoEstructura {
  const fila = matriz.find((permiso) => permiso.rol === rol);
  return fila ? operacionesDe(fila) : SIN_PERMISOS_ESTRUCTURA;
}

function datosDe<T>(estado: EstadoCarga<T>): T | null {
  return estado.tipo === 'listo' ? estado.datos : null;
}

function falloDe<T>(estado: EstadoCarga<T>): FalloHttp | null {
  return estado.tipo === 'error' ? estado.fallo : null;
}

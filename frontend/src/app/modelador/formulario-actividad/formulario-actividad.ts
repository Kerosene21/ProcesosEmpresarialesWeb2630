import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';

import { Aviso } from '../../compartido/aviso/aviso';
import { TEXTO_TIPO_ACTIVIDAD } from '../../compartido/textos';
import { aplicarErroresDelServidor, errorDelServidor } from '../../core/formularios/errores-servidor';
import { entero, noEnBlanco } from '../../core/formularios/validadores';
import { EditarActividad, LONGITUD_MAXIMA_ACTIVIDAD, TIPOS_ACTIVIDAD } from '../../core/modelos/actividad';
import { ActividadRespuesta, DiagramaProceso, LaneRespuesta, PoolRespuesta, TipoActividad } from '../../core/modelos/diagrama';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { ActividadService } from '../../core/servicios/actividad-service';
import {
  GeometriaDiagrama,
  Punto,
  bandaDeLane,
  cajaDeNodo,
  centro,
  colocarEnBanda,
  posicionLibre,
  reubicarEnLane,
} from '../geometria';

export interface PropuestaActividad {
  laneId: number | null;
  posicion: Punto;
}

interface GrupoLanes {
  pool: PoolRespuesta;
  lanes: LaneRespuesta[];
}

type Campo = 'nombre' | 'tipo' | 'laneId' | 'posicionX' | 'posicionY';

const OBLIGATORIO: Record<Campo, string> = {
  nombre: 'El nombre es obligatorio',
  tipo: 'El tipo de actividad es obligatorio',
  laneId: 'La lane responsable es obligatoria',
  posicionX: 'La posición X es obligatoria',
  posicionY: 'La posición Y es obligatoria',
};

const CAMPOS_POR_CODIGO: Readonly<Record<string, Campo>> = {
  ACTIVIDAD_NOMBRE_DUPLICADO: 'nombre',
  LANE_NO_VALIDA: 'laneId',
  SECUENCIA_ENTRE_POOLS: 'laneId',
};

let siguienteFormulario = 0;

@Component({
  selector: 'app-formulario-actividad',
  imports: [ReactiveFormsModule, Aviso],
  templateUrl: './formulario-actividad.html',
  styleUrl: './formulario-actividad.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FormularioActividad {
  private readonly actividades = inject(ActividadService);

  readonly procesoId = input.required<number>();
  readonly diagrama = input.required<DiagramaProceso>();
  readonly geometria = input.required<GeometriaDiagrama>();
  readonly actividad = input<ActividadRespuesta | null>(null);
  readonly propuesta = input<PropuestaActividad | null>(null);
  readonly guardado = output<ActividadRespuesta>();
  readonly cancelado = output<void>();

  protected readonly id = `formulario-actividad-${++siguienteFormulario}`;
  protected readonly tipos = TIPOS_ACTIVIDAD;
  protected readonly textoTipo = TEXTO_TIPO_ACTIVIDAD;
  protected readonly limite = LONGITUD_MAXIMA_ACTIVIDAD.nombre;
  protected readonly edicion = computed(() => this.actividad() !== null);
  protected readonly grupos = computed<GrupoLanes[]>(() => {
    const { pools, lanes } = this.diagrama();
    return [...pools]
      .filter((pool) => !pool.cajaNegra)
      .sort((uno, otro) => uno.orden - otro.orden)
      .map((pool) => ({
        pool,
        lanes: lanes.filter((lane) => lane.poolId === pool.id).sort((una, otra) => una.orden - otra.orden),
      }))
      .filter((grupo) => grupo.lanes.length > 0);
  });

  protected readonly formulario = new FormGroup({
    nombre: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, noEnBlanco, Validators.maxLength(LONGITUD_MAXIMA_ACTIVIDAD.nombre)],
    }),
    tipo: new FormControl<TipoActividad | null>(null, { validators: [Validators.required] }),
    laneId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    posicionX: new FormControl<number | null>(null, { validators: [Validators.required, entero] }),
    posicionY: new FormControl<number | null>(null, { validators: [Validators.required, entero] }),
  });

  protected readonly guardando = signal(false);
  protected readonly fallo = signal<FalloHttp | null>(null);

  constructor() {
    effect(() => {
      const actividad = this.actividad();
      const propuesta = this.propuesta();
      untracked(() => this.preparar(actividad, propuesta));
    });
    this.formulario.controls.laneId.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((laneId) => this.ajustarPosicion(laneId));
  }

  protected error(campo: Campo): string | null {
    const control = this.formulario.controls[campo];
    if (control.disabled || control.valid || !control.touched) {
      return null;
    }
    const servidor = errorDelServidor(control);
    if (servidor) {
      return servidor;
    }
    if (control.hasError('maxlength')) {
      return `El nombre no puede superar ${this.limite} caracteres`;
    }
    if (control.hasError('entero')) {
      return 'Usa un número entero';
    }
    return OBLIGATORIO[campo];
  }

  protected cambiaDeLane(): boolean {
    const actual = this.actividad();
    return actual !== null && this.formulario.controls.laneId.value !== actual.laneId;
  }

  protected guardar(): void {
    if (this.guardando()) {
      return;
    }
    this.fallo.set(null);
    const { nombre, tipo, laneId, posicionX, posicionY } = this.formulario.getRawValue();
    if (this.formulario.invalid || tipo === null || laneId === null) {
      this.formulario.markAllAsTouched();
      return;
    }
    const solicitud = this.enviar({ nombre: nombre.trim(), tipo, laneId }, posicionX, posicionY);
    if (!solicitud) {
      return;
    }
    this.guardando.set(true);
    solicitud.subscribe({
      next: (guardada) => {
        this.guardando.set(false);
        this.guardado.emit(guardada);
      },
      error: (fallo: FalloHttp) => {
        this.guardando.set(false);
        if (!aplicarErroresDelServidor(this.formulario, fallo, CAMPOS_POR_CODIGO)) {
          this.fallo.set(fallo);
        }
      },
    });
  }

  private enviar(datos: EditarActividad, posicionX: number | null, posicionY: number | null): Observable<ActividadRespuesta> | null {
    const actual = this.actividad();
    if (actual) {
      const recolocar = datos.laneId !== actual.laneId && posicionX !== null && posicionY !== null;
      return this.actividades.editar(this.procesoId(), actual.id, recolocar ? { ...datos, posicionX, posicionY } : datos);
    }
    return posicionX === null || posicionY === null
      ? null
      : this.actividades.crear(this.procesoId(), { ...datos, posicionX, posicionY });
  }

  private preparar(actividad: ActividadRespuesta | null, propuesta: PropuestaActividad | null): void {
    this.fallo.set(null);
    const { posicionX, posicionY } = this.formulario.controls;
    if (actividad) {
      this.formulario.reset(
        {
          nombre: actividad.nombre,
          tipo: actividad.tipo,
          laneId: actividad.laneId,
          posicionX: actividad.posicionX,
          posicionY: actividad.posicionY,
        },
        { emitEvent: false },
      );
      posicionX.disable({ emitEvent: false });
      posicionY.disable({ emitEvent: false });
      return;
    }
    posicionX.enable({ emitEvent: false });
    posicionY.enable({ emitEvent: false });
    this.formulario.patchValue(
      {
        laneId: propuesta?.laneId ?? null,
        posicionX: propuesta?.posicion.x ?? null,
        posicionY: propuesta?.posicion.y ?? null,
      },
      { emitEvent: false },
    );
  }

  private ajustarPosicion(laneId: number | null): void {
    const actual = this.actividad();
    const posicion = actual ? this.posicionAlEditar(actual, laneId) : this.posicionAlCrear(laneId);
    if (posicion) {
      this.formulario.patchValue({ posicionX: posicion.x, posicionY: posicion.y }, { emitEvent: false });
    }
  }

  private posicionAlEditar(actividad: ActividadRespuesta, laneId: number | null): Punto | null {
    if (laneId === null || laneId === actividad.laneId) {
      return { x: actividad.posicionX, y: actividad.posicionY };
    }
    return reubicarEnLane(this.diagrama(), this.geometria(), { tipo: 'ACTIVIDAD', id: actividad.id }, laneId);
  }

  private posicionAlCrear(laneId: number | null): Punto | null {
    const geometria = this.geometria();
    const banda = laneId === null ? null : bandaDeLane(geometria, laneId);
    if (!banda) {
      return null;
    }
    const { posicionX, posicionY } = this.formulario.getRawValue();
    return posicionX === null || posicionY === null
      ? posicionLibre(geometria, 'ACTIVIDAD', banda)
      : colocarEnBanda('ACTIVIDAD', centro(cajaDeNodo('ACTIVIDAD', posicionX, posicionY)), banda, geometria.inicioContenido);
  }
}

import { ChangeDetectionStrategy, Component, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';

import { Aviso } from '../../compartido/aviso/aviso';
import { DialogoConfirmacion } from '../../compartido/dialogo-confirmacion/dialogo-confirmacion';
import { TEXTO_TIPO_POOL } from '../../compartido/textos';
import { aplicarErroresDelServidor, errorDelServidor } from '../../core/formularios/errores-servidor';
import { entero, noEnBlanco } from '../../core/formularios/validadores';
import { DiagramaProceso, PoolRespuesta } from '../../core/modelos/diagrama';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { PermisoEstructura } from '../../core/modelos/permiso-estructura';
import { LONGITUD_MAXIMA_POOL, TIPOS_POOL_ADICIONAL, TipoPoolAdicional } from '../../core/modelos/pool';
import { PoolService } from '../../core/servicios/pool-service';

export type PermisosPools = Pick<PermisoEstructura, 'crearPool' | 'editarPool' | 'eliminarPool'>;

export interface ContenidoPool {
  lanes: number;
  actividades: number;
  gateways: number;
  eventos: number;
  flujosEntrantes: number;
}

type Campo = 'nombre' | 'tipo' | 'empresaParticipanteId' | 'cajaNegra';

const CAMPOS_POR_CODIGO: Readonly<Record<string, Campo>> = {
  POOL_CON_CONTENIDO: 'cajaNegra',
};

const AYUDA_TIPO: Record<TipoPoolAdicional, string> = {
  PARTICIPANTE: 'Otra empresa registrada en el sistema. Indica su identificador.',
  EXTERNO: 'Un participante que no está registrado, como un banco o un sistema de terceros.',
};

export function contenidoDePool(diagrama: DiagramaProceso, poolId: number): ContenidoPool {
  return {
    lanes: diagrama.lanes.filter((lane) => lane.poolId === poolId).length,
    actividades: diagrama.actividades.filter((actividad) => actividad.poolId === poolId).length,
    gateways: diagrama.gateways.filter((gateway) => gateway.poolId === poolId).length,
    eventos:
      diagrama.messageThrows.filter((evento) => evento.poolOrigenId === poolId).length +
      diagrama.messageCatches.filter((evento) => evento.poolId === poolId).length +
      diagrama.enviosExternos.filter((evento) => evento.poolOrigenId === poolId).length,
    flujosEntrantes: diagrama.flujosMensaje.filter((flujo) => flujo.poolDestinoId === poolId).length,
  };
}

export function tieneContenido(contenido: ContenidoPool): boolean {
  return contenido.lanes + contenido.actividades + contenido.gateways + contenido.eventos > 0;
}

function formularioPool() {
  return new FormGroup({
    nombre: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, noEnBlanco, Validators.maxLength(LONGITUD_MAXIMA_POOL.nombre)],
    }),
    tipo: new FormControl<TipoPoolAdicional>('EXTERNO', { nonNullable: true }),
    cajaNegra: new FormControl(false, { nonNullable: true }),
    empresaParticipanteId: new FormControl<number | null>(null, { validators: [entero, Validators.min(1)] }),
  });
}

type FormularioPool = ReturnType<typeof formularioPool>;

@Component({
  selector: 'app-panel-pools',
  imports: [ReactiveFormsModule, Aviso, DialogoConfirmacion],
  templateUrl: './panel-pools.html',
  styleUrl: './panel-pools.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PanelPools {
  private readonly poolsApi = inject(PoolService);

  readonly procesoId = input.required<number>();
  readonly diagrama = input.required<DiagramaProceso>();
  readonly permisos = input.required<PermisosPools>();
  readonly cambio = output<void>();
  readonly rechazo = output<void>();

  protected readonly tipos = TIPOS_POOL_ADICIONAL;
  protected readonly textoTipo = TEXTO_TIPO_POOL;
  protected readonly ayudaTipo = AYUDA_TIPO;
  protected readonly limite = LONGITUD_MAXIMA_POOL.nombre;

  protected readonly pools = computed(() =>
    [...this.diagrama().pools].sort((uno, otro) => uno.orden - otro.orden || uno.id - otro.id),
  );
  private readonly contenidos = computed(
    () => new Map(this.pools().map((pool) => [pool.id, contenidoDePool(this.diagrama(), pool.id)])),
  );
  protected readonly puedeModificar = computed(() => {
    const permisos = this.permisos();
    return permisos.crearPool || permisos.editarPool || permisos.eliminarPool;
  });

  protected readonly creacion = formularioPool();
  protected readonly edicion = formularioPool();
  protected readonly tipoNuevo = signal<TipoPoolAdicional>('EXTERNO');
  protected readonly editando = signal<PoolRespuesta | null>(null);
  protected readonly procesando = signal(false);
  protected readonly fallo = signal<FalloHttp | null>(null);
  protected readonly mensaje = signal<string | null>(null);
  protected readonly porEliminar = signal<PoolRespuesta | null>(null);
  protected readonly falloEliminacion = signal<string | null>(null);

  constructor() {
    this.creacion.controls.tipo.valueChanges.pipe(takeUntilDestroyed()).subscribe((tipo) => {
      this.tipoNuevo.set(tipo);
      this.exigirEmpresa(this.creacion, tipo === 'PARTICIPANTE');
    });
  }

  protected contenido(pool: PoolRespuesta): ContenidoPool {
    return this.contenidos().get(pool.id) ?? contenidoDePool(this.diagrama(), pool.id);
  }

  protected resumen(pool: PoolRespuesta): string {
    if (pool.cajaNegra) {
      return 'Caja negra: sin lanes ni elementos internos';
    }
    const { lanes, actividades, gateways, eventos } = this.contenido(pool);
    return `${lanes} ${lanes === 1 ? 'lane' : 'lanes'} · ${actividades} ${actividades === 1 ? 'actividad' : 'actividades'} · ${gateways} ${gateways === 1 ? 'gateway' : 'gateways'} · ${eventos} ${eventos === 1 ? 'evento' : 'eventos'}`;
  }

  protected motivoNoEliminable(pool: PoolRespuesta): string | null {
    if (pool.tipo === 'PROPIETARIO') {
      return 'El pool propietario se crea con el proceso y no se elimina';
    }
    const contenido = this.contenido(pool);
    if (tieneContenido(contenido)) {
      return 'Elimina primero sus lanes, actividades, gateways y eventos';
    }
    if (contenido.flujosEntrantes > 0) {
      return 'Recibe flujos de mensaje: elimina o redirige primero esos mensajes';
    }
    return null;
  }

  protected crear(): void {
    this.limpiarMensajes();
    const { nombre, tipo, cajaNegra, empresaParticipanteId } = this.creacion.getRawValue();
    if (this.creacion.invalid) {
      this.creacion.markAllAsTouched();
      return;
    }
    this.ejecutar(
      this.poolsApi.crear(this.procesoId(), {
        nombre: nombre.trim(),
        tipo,
        cajaNegra,
        empresaParticipanteId: tipo === 'PARTICIPANTE' ? empresaParticipanteId : null,
      }),
      (pool) => `El pool «${pool.nombre}» se creó y aparece en el diagrama.`,
      this.creacion,
      () => {
        this.creacion.reset();
        this.tipoNuevo.set('EXTERNO');
      },
    );
  }

  protected iniciarEdicion(pool: PoolRespuesta): void {
    this.limpiarMensajes();
    this.editando.set(pool);
    this.edicion.reset({
      nombre: pool.nombre,
      tipo: pool.tipo === 'PROPIETARIO' ? 'EXTERNO' : pool.tipo,
      cajaNegra: pool.cajaNegra,
      empresaParticipanteId: pool.tipo === 'PARTICIPANTE' ? pool.empresaId : null,
    });
    this.exigirEmpresa(this.edicion, pool.tipo === 'PARTICIPANTE');
    if (pool.tipo === 'PROPIETARIO') {
      this.edicion.controls.cajaNegra.disable();
    } else {
      this.edicion.controls.cajaNegra.enable();
    }
  }

  protected cancelarEdicion(): void {
    this.editando.set(null);
    this.edicion.reset();
  }

  protected guardarEdicion(): void {
    const pool = this.editando();
    this.limpiarMensajes();
    const { nombre, cajaNegra, empresaParticipanteId } = this.edicion.getRawValue();
    if (!pool) {
      return;
    }
    if (this.edicion.invalid) {
      this.edicion.markAllAsTouched();
      return;
    }
    this.ejecutar(
      this.poolsApi.editar(this.procesoId(), pool.id, {
        nombre: nombre.trim(),
        cajaNegra: pool.tipo === 'PROPIETARIO' ? false : cajaNegra,
        empresaParticipanteId: pool.tipo === 'PARTICIPANTE' ? empresaParticipanteId : null,
      }),
      (actualizado) => `El pool «${actualizado.nombre}» se actualizó.`,
      this.edicion,
      () => this.cancelarEdicion(),
    );
  }

  protected solicitarEliminacion(pool: PoolRespuesta): void {
    this.limpiarMensajes();
    this.falloEliminacion.set(null);
    this.porEliminar.set(pool);
  }

  protected cancelarEliminacion(): void {
    this.porEliminar.set(null);
    this.falloEliminacion.set(null);
  }

  protected eliminar(): void {
    const pool = this.porEliminar();
    if (!pool || this.procesando()) {
      return;
    }
    this.procesando.set(true);
    this.falloEliminacion.set(null);
    this.poolsApi.eliminar(this.procesoId(), pool.id).subscribe({
      next: () => {
        this.procesando.set(false);
        this.porEliminar.set(null);
        this.mensaje.set(`El pool «${pool.nombre}» se eliminó. Los demás pools se reordenaron.`);
        this.cambio.emit();
      },
      error: (fallo: FalloHttp) => {
        this.procesando.set(false);
        this.falloEliminacion.set(fallo.detalle ?? fallo.titulo);
        this.avisarRechazo(fallo);
      },
    });
  }

  protected error(formulario: FormularioPool, campo: Campo): string | null {
    const control = formulario.controls[campo];
    if (control.disabled || control.valid || !control.touched) {
      return null;
    }
    const servidor = errorDelServidor(control);
    if (servidor) {
      return servidor;
    }
    if (campo === 'empresaParticipanteId') {
      return control.hasError('required')
        ? 'Indica el identificador de la empresa participante'
        : 'Usa un identificador entero mayor que cero';
    }
    return control.hasError('maxlength')
      ? `El nombre no puede superar ${this.limite} caracteres`
      : 'El nombre del pool es obligatorio';
  }

  private ejecutar(
    solicitud: Observable<PoolRespuesta>,
    exito: (pool: PoolRespuesta) => string,
    formulario: FormularioPool,
    alTerminar: () => void,
  ): void {
    if (this.procesando()) {
      return;
    }
    this.procesando.set(true);
    solicitud.subscribe({
      next: (pool) => {
        this.procesando.set(false);
        this.mensaje.set(exito(pool));
        alTerminar();
        this.cambio.emit();
      },
      error: (fallo: FalloHttp) => {
        this.procesando.set(false);
        if (!aplicarErroresDelServidor(formulario, fallo, CAMPOS_POR_CODIGO)) {
          this.fallo.set(fallo);
        }
        this.avisarRechazo(fallo);
      },
    });
  }

  private exigirEmpresa(formulario: FormularioPool, exigida: boolean): void {
    const control = formulario.controls.empresaParticipanteId;
    control.setValidators(exigida ? [Validators.required, entero, Validators.min(1)] : [entero, Validators.min(1)]);
    if (!exigida) {
      control.setValue(null, { emitEvent: false });
    }
    control.updateValueAndValidity({ emitEvent: false });
  }

  private avisarRechazo(fallo: FalloHttp): void {
    if (fallo.tipo === 'sin-permiso') {
      this.rechazo.emit();
    }
  }

  private limpiarMensajes(): void {
    this.fallo.set(null);
    this.mensaje.set(null);
  }
}

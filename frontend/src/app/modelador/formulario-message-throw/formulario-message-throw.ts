import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';

import { Aviso } from '../../compartido/aviso/aviso';
import { TEXTO_SIN_CASO } from '../../compartido/textos';
import { aplicarErroresDelServidor } from '../../core/formularios/errores-servidor';
import { entero, noEnBlanco } from '../../core/formularios/validadores';
import { ComportamientoSinCaso, DiagramaProceso, MessageThrowRespuesta } from '../../core/modelos/diagrama';
import { COMPORTAMIENTOS_SIN_CASO, LONGITUD_MAXIMA_EVENTO } from '../../core/modelos/evento';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { MessageThrowService } from '../../core/servicios/message-throw-service';
import { PropuestaEvento, errorDeCampo, nombreDePool, poolsConElementos, poolsDestino, textoONulo } from '../eventos';
import { GeometriaDiagrama, bandaDePool, cajaDeNodo, centro, colocarEnBanda } from '../geometria';

type Campo =
  | 'nombreMensaje'
  | 'contenido'
  | 'poolOrigenId'
  | 'poolDestinoId'
  | 'claveCorrelacion'
  | 'comportamientoSinCaso'
  | 'posicionX'
  | 'posicionY';

const OBLIGATORIO: Record<Campo, string> = {
  nombreMensaje: 'El nombre del mensaje es obligatorio',
  contenido: 'Documenta el contenido del mensaje',
  poolOrigenId: 'Elige el pool que emite el mensaje',
  poolDestinoId: 'Elige el pool que recibe el mensaje',
  claveCorrelacion: '',
  comportamientoSinCaso: '',
  posicionX: 'La posición X es obligatoria',
  posicionY: 'La posición Y es obligatoria',
};

const MAXIMO: Partial<Record<Campo, number>> = {
  nombreMensaje: LONGITUD_MAXIMA_EVENTO.nombreMensaje,
  contenido: LONGITUD_MAXIMA_EVENTO.contenido,
  claveCorrelacion: LONGITUD_MAXIMA_EVENTO.claveCorrelacion,
};

const CAMPOS_POR_CODIGO: Readonly<Record<string, Campo>> = {
  POOL_MENSAJE_NO_VALIDO: 'poolDestinoId',
  MENSAJE_ENTRE_MISMO_POOL: 'poolDestinoId',
  POOL_CAJA_NEGRA: 'poolOrigenId',
  POOL_NO_VALIDO: 'poolOrigenId',
  CORRELACION_NO_VALIDA: 'comportamientoSinCaso',
};

let siguienteFormulario = 0;

@Component({
  selector: 'app-formulario-message-throw',
  imports: [ReactiveFormsModule, Aviso],
  templateUrl: './formulario-message-throw.html',
  styleUrl: './formulario-message-throw.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FormularioMessageThrow {
  private readonly messageThrows = inject(MessageThrowService);

  readonly procesoId = input.required<number>();
  readonly diagrama = input.required<DiagramaProceso>();
  readonly geometria = input.required<GeometriaDiagrama>();
  readonly messageThrow = input<MessageThrowRespuesta | null>(null);
  readonly propuesta = input<PropuestaEvento | null>(null);
  readonly guardado = output<MessageThrowRespuesta>();
  readonly cancelado = output<void>();

  protected readonly id = `formulario-message-throw-${++siguienteFormulario}`;
  protected readonly limites = LONGITUD_MAXIMA_EVENTO;
  protected readonly comportamientos = COMPORTAMIENTOS_SIN_CASO;
  protected readonly textoSinCaso = TEXTO_SIN_CASO;
  protected readonly nombrePool = nombreDePool;
  protected readonly edicion = computed(() => this.messageThrow() !== null);
  protected readonly origenes = computed(() => poolsConElementos(this.diagrama()));
  private readonly origenElegido = signal<number | null>(null);
  protected readonly destinos = computed(() => poolsDestino(this.diagrama(), this.origenElegido()));

  protected readonly formulario = new FormGroup({
    nombreMensaje: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, noEnBlanco, Validators.maxLength(LONGITUD_MAXIMA_EVENTO.nombreMensaje)],
    }),
    contenido: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, noEnBlanco, Validators.maxLength(LONGITUD_MAXIMA_EVENTO.contenido)],
    }),
    poolOrigenId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    poolDestinoId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    claveCorrelacion: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(LONGITUD_MAXIMA_EVENTO.claveCorrelacion)],
    }),
    comportamientoSinCaso: new FormControl<ComportamientoSinCaso | null>(null),
    posicionX: new FormControl<number | null>(null, { validators: [Validators.required, entero] }),
    posicionY: new FormControl<number | null>(null, { validators: [Validators.required, entero] }),
  });

  protected readonly guardando = signal(false);
  protected readonly fallo = signal<FalloHttp | null>(null);

  constructor() {
    effect(() => {
      const messageThrow = this.messageThrow();
      const propuesta = this.propuesta();
      untracked(() => this.preparar(messageThrow, propuesta));
    });
    this.formulario.controls.poolOrigenId.valueChanges.pipe(takeUntilDestroyed()).subscribe((origen) => {
      this.elegirOrigen(origen);
      this.ajustarPosicion(origen);
    });
  }

  protected error(campo: Campo): string | null {
    return errorDeCampo(this.formulario.controls[campo], OBLIGATORIO[campo], MAXIMO[campo]);
  }

  protected guardar(): void {
    if (this.guardando()) {
      return;
    }
    this.fallo.set(null);
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    const solicitud = this.enviar();
    if (!solicitud) {
      return;
    }
    this.guardando.set(true);
    solicitud.subscribe({
      next: (guardado) => {
        this.guardando.set(false);
        this.guardado.emit(guardado);
      },
      error: (fallo: FalloHttp) => {
        this.guardando.set(false);
        if (!aplicarErroresDelServidor(this.formulario, fallo, CAMPOS_POR_CODIGO)) {
          this.fallo.set(fallo);
        }
      },
    });
  }

  private enviar(): Observable<MessageThrowRespuesta> | null {
    const valores = this.formulario.getRawValue();
    if (valores.poolDestinoId === null) {
      return null;
    }
    const datos = {
      nombreMensaje: valores.nombreMensaje.trim(),
      contenido: valores.contenido.trim(),
      poolDestinoId: valores.poolDestinoId,
      claveCorrelacion: textoONulo(valores.claveCorrelacion),
      comportamientoSinCaso: valores.comportamientoSinCaso,
    };
    const actual = this.messageThrow();
    if (actual) {
      return this.messageThrows.editar(this.procesoId(), actual.id, datos);
    }
    const { poolOrigenId, posicionX, posicionY } = valores;
    return poolOrigenId === null || posicionX === null || posicionY === null
      ? null
      : this.messageThrows.crear(this.procesoId(), { ...datos, poolOrigenId, posicionX, posicionY });
  }

  private preparar(messageThrow: MessageThrowRespuesta | null, propuesta: PropuestaEvento | null): void {
    this.fallo.set(null);
    const { poolOrigenId, posicionX, posicionY } = this.formulario.controls;
    if (messageThrow) {
      this.formulario.reset(
        {
          nombreMensaje: messageThrow.nombreMensaje,
          contenido: messageThrow.contenido,
          poolOrigenId: messageThrow.poolOrigenId,
          poolDestinoId: messageThrow.poolDestinoId,
          claveCorrelacion: messageThrow.claveCorrelacion ?? '',
          comportamientoSinCaso: messageThrow.comportamientoSinCaso,
          posicionX: messageThrow.posicionX,
          posicionY: messageThrow.posicionY,
        },
        { emitEvent: false },
      );
      [poolOrigenId, posicionX, posicionY].forEach((control) => control.disable({ emitEvent: false }));
    } else {
      [poolOrigenId, posicionX, posicionY].forEach((control) => control.enable({ emitEvent: false }));
      this.formulario.reset(
        {
          poolOrigenId: propuesta?.poolId ?? this.origenes()[0]?.id ?? null,
          posicionX: propuesta?.posicion.x ?? null,
          posicionY: propuesta?.posicion.y ?? null,
        },
        { emitEvent: false },
      );
    }
    this.elegirOrigen(poolOrigenId.value);
  }

  private elegirOrigen(origen: number | null): void {
    this.origenElegido.set(origen);
    const destino = this.formulario.controls.poolDestinoId;
    if (destino.value !== null && destino.value === origen) {
      destino.setValue(null, { emitEvent: false });
    }
  }

  private ajustarPosicion(poolId: number | null): void {
    const geometria = this.geometria();
    const banda = poolId === null || this.edicion() ? null : bandaDePool(geometria, poolId);
    const { posicionX, posicionY } = this.formulario.getRawValue();
    if (!banda || posicionX === null || posicionY === null) {
      return;
    }
    const posicion = colocarEnBanda('EVENTO', centro(cajaDeNodo('EVENTO', posicionX, posicionY)), banda, geometria.inicioContenido);
    this.formulario.patchValue({ posicionX: posicion.x, posicionY: posicion.y }, { emitEvent: false });
  }
}

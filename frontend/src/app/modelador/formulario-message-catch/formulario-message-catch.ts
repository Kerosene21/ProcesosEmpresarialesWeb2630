import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';

import { Aviso } from '../../compartido/aviso/aviso';
import { TEXTO_SIN_CASO, TEXTO_VARIANTE_CATCH } from '../../compartido/textos';
import { aplicarErroresDelServidor } from '../../core/formularios/errores-servidor';
import { entero, noEnBlanco } from '../../core/formularios/validadores';
import {
  ComportamientoSinCaso,
  DiagramaProceso,
  MessageCatchRespuesta,
  VarianteMessageCatch,
} from '../../core/modelos/diagrama';
import { COMPORTAMIENTOS_SIN_CASO, LONGITUD_MAXIMA_EVENTO, VARIANTES_CATCH } from '../../core/modelos/evento';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { MessageCatchService } from '../../core/servicios/message-catch-service';
import { PropuestaEvento, errorDeCampo, nombreDePool, poolsConElementos, textoONulo } from '../eventos';
import { GeometriaDiagrama, bandaDePool, cajaDeNodo, centro, colocarEnBanda } from '../geometria';

type Campo =
  | 'nombreMensaje'
  | 'variante'
  | 'datosEsperados'
  | 'actividadesUso'
  | 'claveCorrelacion'
  | 'comportamientoSinCaso'
  | 'poolId'
  | 'posicionX'
  | 'posicionY';

const OBLIGATORIO: Record<Campo, string> = {
  nombreMensaje: 'El nombre del mensaje esperado es obligatorio',
  variante: 'Indica si el evento es de inicio o intermedio',
  datosEsperados: 'Documenta los datos que se esperan',
  actividadesUso: 'Indica qué actividades usan los datos recibidos',
  claveCorrelacion: '',
  comportamientoSinCaso: '',
  poolId: 'Elige el pool que recibe el mensaje',
  posicionX: 'La posición X es obligatoria',
  posicionY: 'La posición Y es obligatoria',
};

const MAXIMO: Partial<Record<Campo, number>> = {
  nombreMensaje: LONGITUD_MAXIMA_EVENTO.nombreMensaje,
  datosEsperados: LONGITUD_MAXIMA_EVENTO.datosEsperados,
  actividadesUso: LONGITUD_MAXIMA_EVENTO.actividadesUso,
  claveCorrelacion: LONGITUD_MAXIMA_EVENTO.claveCorrelacion,
};

const CAMPOS_POR_CODIGO: Readonly<Record<string, Campo>> = {
  CORRELACION_NO_VALIDA: 'comportamientoSinCaso',
  CATCH_INICIO_CON_ENTRADA: 'variante',
  POOL_CAJA_NEGRA: 'poolId',
  POOL_NO_VALIDO: 'poolId',
};

let siguienteFormulario = 0;

@Component({
  selector: 'app-formulario-message-catch',
  imports: [ReactiveFormsModule, Aviso],
  templateUrl: './formulario-message-catch.html',
  styleUrl: './formulario-message-catch.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FormularioMessageCatch {
  private readonly messageCatches = inject(MessageCatchService);

  readonly procesoId = input.required<number>();
  readonly diagrama = input.required<DiagramaProceso>();
  readonly geometria = input.required<GeometriaDiagrama>();
  readonly messageCatch = input<MessageCatchRespuesta | null>(null);
  readonly propuesta = input<PropuestaEvento | null>(null);
  readonly guardado = output<MessageCatchRespuesta>();
  readonly cancelado = output<void>();

  protected readonly id = `formulario-message-catch-${++siguienteFormulario}`;
  protected readonly variantes = VARIANTES_CATCH;
  protected readonly textoVariante = TEXTO_VARIANTE_CATCH;
  protected readonly comportamientos = COMPORTAMIENTOS_SIN_CASO;
  protected readonly textoSinCaso = TEXTO_SIN_CASO;
  protected readonly nombrePool = nombreDePool;
  protected readonly edicion = computed(() => this.messageCatch() !== null);
  protected readonly pools = computed(() => poolsConElementos(this.diagrama()));
  protected readonly varianteElegida = signal<VarianteMessageCatch | null>(null);
  protected readonly entradas = computed(() => {
    const actual = this.messageCatch();
    return actual
      ? this.diagrama().arcos.filter((arco) => arco.destinoTipo === 'EVENTO' && arco.destinoId === actual.id).length
      : 0;
  });

  protected readonly formulario = new FormGroup({
    nombreMensaje: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, noEnBlanco, Validators.maxLength(LONGITUD_MAXIMA_EVENTO.nombreMensaje)],
    }),
    variante: new FormControl<VarianteMessageCatch | null>(null, { validators: [Validators.required] }),
    datosEsperados: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, noEnBlanco, Validators.maxLength(LONGITUD_MAXIMA_EVENTO.datosEsperados)],
    }),
    actividadesUso: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, noEnBlanco, Validators.maxLength(LONGITUD_MAXIMA_EVENTO.actividadesUso)],
    }),
    origenExterno: new FormControl(false, { nonNullable: true }),
    claveCorrelacion: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(LONGITUD_MAXIMA_EVENTO.claveCorrelacion)],
    }),
    comportamientoSinCaso: new FormControl<ComportamientoSinCaso | null>(null),
    poolId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    posicionX: new FormControl<number | null>(null, { validators: [Validators.required, entero] }),
    posicionY: new FormControl<number | null>(null, { validators: [Validators.required, entero] }),
  });

  protected readonly guardando = signal(false);
  protected readonly fallo = signal<FalloHttp | null>(null);

  constructor() {
    effect(() => {
      const messageCatch = this.messageCatch();
      const propuesta = this.propuesta();
      untracked(() => this.preparar(messageCatch, propuesta));
    });
    const { variante, poolId } = this.formulario.controls;
    variante.valueChanges.pipe(takeUntilDestroyed()).subscribe((valor) => this.aplicarVariante(valor));
    poolId.valueChanges.pipe(takeUntilDestroyed()).subscribe((valor) => this.ajustarPosicion(valor));
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

  private enviar(): Observable<MessageCatchRespuesta> | null {
    const valores = this.formulario.getRawValue();
    if (valores.variante === null) {
      return null;
    }
    const datos = {
      nombreMensaje: valores.nombreMensaje.trim(),
      variante: valores.variante,
      datosEsperados: valores.datosEsperados.trim(),
      actividadesUso: valores.actividadesUso.trim(),
      origenExterno: valores.origenExterno,
      claveCorrelacion: textoONulo(valores.claveCorrelacion),
      comportamientoSinCaso: valores.comportamientoSinCaso,
    };
    const actual = this.messageCatch();
    if (actual) {
      return this.messageCatches.editar(this.procesoId(), actual.id, datos);
    }
    const { poolId, posicionX, posicionY } = valores;
    return poolId === null || posicionX === null || posicionY === null
      ? null
      : this.messageCatches.crear(this.procesoId(), { ...datos, poolId, posicionX, posicionY });
  }

  private preparar(messageCatch: MessageCatchRespuesta | null, propuesta: PropuestaEvento | null): void {
    this.fallo.set(null);
    const { poolId, posicionX, posicionY } = this.formulario.controls;
    if (messageCatch) {
      this.formulario.reset(
        {
          nombreMensaje: messageCatch.nombreMensaje,
          variante: messageCatch.variante,
          datosEsperados: messageCatch.datosEsperados,
          actividadesUso: messageCatch.actividadesUso,
          origenExterno: messageCatch.origenExterno,
          claveCorrelacion: messageCatch.claveCorrelacion ?? '',
          comportamientoSinCaso: messageCatch.comportamientoSinCaso,
          poolId: messageCatch.poolId,
          posicionX: messageCatch.posicionX,
          posicionY: messageCatch.posicionY,
        },
        { emitEvent: false },
      );
      [poolId, posicionX, posicionY].forEach((control) => control.disable({ emitEvent: false }));
    } else {
      [poolId, posicionX, posicionY].forEach((control) => control.enable({ emitEvent: false }));
      this.formulario.reset(
        {
          poolId: propuesta?.poolId ?? this.pools()[0]?.id ?? null,
          posicionX: propuesta?.posicion.x ?? null,
          posicionY: propuesta?.posicion.y ?? null,
        },
        { emitEvent: false },
      );
    }
    this.aplicarVariante(this.formulario.controls.variante.value);
  }

  private aplicarVariante(variante: VarianteMessageCatch | null): void {
    this.varianteElegida.set(variante);
    const comportamiento = this.formulario.controls.comportamientoSinCaso;
    if (variante === 'INICIO') {
      comportamiento.setValue('INICIAR_NUEVO_CASO', { emitEvent: false });
      comportamiento.disable({ emitEvent: false });
    } else {
      comportamiento.enable({ emitEvent: false });
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

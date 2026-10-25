import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';

import { Aviso } from '../../compartido/aviso/aviso';
import { TEXTO_DESTINO_EXTERNO, TEXTO_FALLO_ENVIO } from '../../compartido/textos';
import { aplicarErroresDelServidor } from '../../core/formularios/errores-servidor';
import { entero, noEnBlanco } from '../../core/formularios/validadores';
import {
  ComportamientoFallo,
  DiagramaProceso,
  EnvioExternoRespuesta,
  TipoDestinoExterno,
} from '../../core/modelos/diagrama';
import { COMPORTAMIENTOS_FALLO, LONGITUD_MAXIMA_EVENTO, TIPOS_DESTINO_EXTERNO } from '../../core/modelos/evento';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { EnvioExternoService } from '../../core/servicios/envio-externo-service';
import { PropuestaEvento, errorDeCampo, nombreDePool, poolsConElementos, sistemasExternos, textoONulo } from '../eventos';
import { GeometriaDiagrama, bandaDePool, cajaDeNodo, centro, colocarEnBanda } from '../geometria';

type Campo =
  | 'nombreMensaje'
  | 'poolOrigenId'
  | 'poolDestinoId'
  | 'tipoDestino'
  | 'datosEnviados'
  | 'momentoProceso'
  | 'comportamientoFallo'
  | 'claveCorrelacion'
  | 'posicionX'
  | 'posicionY';

const OBLIGATORIO: Record<Campo, string> = {
  nombreMensaje: 'El nombre del envío es obligatorio',
  poolOrigenId: 'Elige el pool que realiza el envío',
  poolDestinoId: 'Elige el sistema externo que recibe el envío',
  tipoDestino: 'Indica el medio del envío',
  datosEnviados: 'Documenta los datos que se envían',
  momentoProceso: 'Indica en qué momento del proceso ocurre',
  comportamientoFallo: 'Indica qué hace el proceso si el envío falla',
  claveCorrelacion: '',
  posicionX: 'La posición X es obligatoria',
  posicionY: 'La posición Y es obligatoria',
};

const MAXIMO: Partial<Record<Campo, number>> = {
  nombreMensaje: LONGITUD_MAXIMA_EVENTO.nombreMensaje,
  datosEnviados: LONGITUD_MAXIMA_EVENTO.datosEnviados,
  momentoProceso: LONGITUD_MAXIMA_EVENTO.momentoProceso,
  claveCorrelacion: LONGITUD_MAXIMA_EVENTO.claveCorrelacion,
};

const CAMPOS_POR_CODIGO: Readonly<Record<string, Campo>> = {
  ENVIO_EXTERNO_NO_VALIDO: 'poolDestinoId',
  POOL_MENSAJE_NO_VALIDO: 'poolDestinoId',
  MENSAJE_ENTRE_MISMO_POOL: 'poolDestinoId',
  POOL_CAJA_NEGRA: 'poolOrigenId',
  POOL_NO_VALIDO: 'poolOrigenId',
};

let siguienteFormulario = 0;

@Component({
  selector: 'app-formulario-envio-externo',
  imports: [ReactiveFormsModule, Aviso],
  templateUrl: './formulario-envio-externo.html',
  styleUrl: './formulario-envio-externo.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FormularioEnvioExterno {
  private readonly envios = inject(EnvioExternoService);

  readonly procesoId = input.required<number>();
  readonly diagrama = input.required<DiagramaProceso>();
  readonly geometria = input.required<GeometriaDiagrama>();
  readonly envio = input<EnvioExternoRespuesta | null>(null);
  readonly propuesta = input<PropuestaEvento | null>(null);
  readonly guardado = output<EnvioExternoRespuesta>();
  readonly cancelado = output<void>();

  protected readonly id = `formulario-envio-externo-${++siguienteFormulario}`;
  protected readonly tiposDestino = TIPOS_DESTINO_EXTERNO;
  protected readonly textoDestino = TEXTO_DESTINO_EXTERNO;
  protected readonly comportamientos = COMPORTAMIENTOS_FALLO;
  protected readonly textoFallo = TEXTO_FALLO_ENVIO;
  protected readonly nombrePool = nombreDePool;
  protected readonly edicion = computed(() => this.envio() !== null);
  protected readonly origenes = computed(() => poolsConElementos(this.diagrama()));
  protected readonly destinos = computed(() => sistemasExternos(this.diagrama()));

  protected readonly formulario = new FormGroup({
    nombreMensaje: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, noEnBlanco, Validators.maxLength(LONGITUD_MAXIMA_EVENTO.nombreMensaje)],
    }),
    poolOrigenId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    poolDestinoId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    tipoDestino: new FormControl<TipoDestinoExterno | null>(null, { validators: [Validators.required] }),
    datosEnviados: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, noEnBlanco, Validators.maxLength(LONGITUD_MAXIMA_EVENTO.datosEnviados)],
    }),
    momentoProceso: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, noEnBlanco, Validators.maxLength(LONGITUD_MAXIMA_EVENTO.momentoProceso)],
    }),
    comportamientoFallo: new FormControl<ComportamientoFallo | null>(null, { validators: [Validators.required] }),
    claveCorrelacion: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(LONGITUD_MAXIMA_EVENTO.claveCorrelacion)],
    }),
    posicionX: new FormControl<number | null>(null, { validators: [Validators.required, entero] }),
    posicionY: new FormControl<number | null>(null, { validators: [Validators.required, entero] }),
  });

  protected readonly guardando = signal(false);
  protected readonly fallo = signal<FalloHttp | null>(null);

  constructor() {
    effect(() => {
      const envio = this.envio();
      const propuesta = this.propuesta();
      untracked(() => this.preparar(envio, propuesta));
    });
    this.formulario.controls.poolOrigenId.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((origen) => this.ajustarPosicion(origen));
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

  private enviar(): Observable<EnvioExternoRespuesta> | null {
    const valores = this.formulario.getRawValue();
    const { poolDestinoId, tipoDestino, comportamientoFallo } = valores;
    if (poolDestinoId === null || tipoDestino === null || comportamientoFallo === null) {
      return null;
    }
    const datos = {
      nombreMensaje: valores.nombreMensaje.trim(),
      poolDestinoId,
      tipoDestino,
      datosEnviados: valores.datosEnviados.trim(),
      momentoProceso: valores.momentoProceso.trim(),
      comportamientoFallo,
      claveCorrelacion: textoONulo(valores.claveCorrelacion),
    };
    const actual = this.envio();
    if (actual) {
      return this.envios.editar(this.procesoId(), actual.id, datos);
    }
    const { poolOrigenId, posicionX, posicionY } = valores;
    return poolOrigenId === null || posicionX === null || posicionY === null
      ? null
      : this.envios.crear(this.procesoId(), { ...datos, poolOrigenId, posicionX, posicionY });
  }

  private preparar(envio: EnvioExternoRespuesta | null, propuesta: PropuestaEvento | null): void {
    this.fallo.set(null);
    const { poolOrigenId, posicionX, posicionY } = this.formulario.controls;
    if (envio) {
      this.formulario.reset(
        {
          nombreMensaje: envio.nombreMensaje,
          poolOrigenId: envio.poolOrigenId,
          poolDestinoId: envio.poolDestinoId,
          tipoDestino: envio.tipoDestino,
          datosEnviados: envio.datosEnviados,
          momentoProceso: envio.momentoProceso,
          comportamientoFallo: envio.comportamientoFallo,
          claveCorrelacion: envio.claveCorrelacion ?? '',
          posicionX: envio.posicionX,
          posicionY: envio.posicionY,
        },
        { emitEvent: false },
      );
      [poolOrigenId, posicionX, posicionY].forEach((control) => control.disable({ emitEvent: false }));
    } else {
      [poolOrigenId, posicionX, posicionY].forEach((control) => control.enable({ emitEvent: false }));
      this.formulario.reset(
        {
          poolOrigenId: propuesta?.poolId ?? this.origenes()[0]?.id ?? null,
          poolDestinoId: this.destinos().length === 1 ? this.destinos()[0].id : null,
          posicionX: propuesta?.posicion.x ?? null,
          posicionY: propuesta?.posicion.y ?? null,
        },
        { emitEvent: false },
      );
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

import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, FormRecord, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable } from 'rxjs';

import { Aviso } from '../../compartido/aviso/aviso';
import { TEXTO_TIPO_GATEWAY } from '../../compartido/textos';
import { aplicarErroresDelServidor, errorDelServidor } from '../../core/formularios/errores-servidor';
import { entero, noEnBlanco } from '../../core/formularios/validadores';
import { ArcoRespuesta, DiagramaProceso, GatewayRespuesta, TipoGateway } from '../../core/modelos/diagrama';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { LONGITUD_MAXIMA_CONDICION, TIPOS_GATEWAY, exigeCondicion } from '../../core/modelos/gateway';
import { GatewayService } from '../../core/servicios/gateway-service';
import { GeometriaDiagrama, Punto, bandaDePool, cajaDeNodo, centro, colocarEnBanda } from '../geometria';

export interface PropuestaGateway {
  poolId: number | null;
  posicion: Punto;
}

type Campo = 'tipo' | 'poolId' | 'posicionX' | 'posicionY';

const OBLIGATORIO: Record<Campo, string> = {
  tipo: 'El tipo de gateway es obligatorio',
  poolId: 'Elige el pool del gateway',
  posicionX: 'La posición X es obligatoria',
  posicionY: 'La posición Y es obligatoria',
};

const CAMPOS_POR_CODIGO: Readonly<Record<string, Campo>> = {
  POOL_NO_VALIDO: 'poolId',
  POOL_CAJA_NEGRA: 'poolId',
};

let siguienteFormulario = 0;

@Component({
  selector: 'app-formulario-gateway',
  imports: [ReactiveFormsModule, Aviso],
  templateUrl: './formulario-gateway.html',
  styleUrl: './formulario-gateway.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FormularioGateway {
  private readonly gateways = inject(GatewayService);

  readonly procesoId = input.required<number>();
  readonly diagrama = input.required<DiagramaProceso>();
  readonly geometria = input.required<GeometriaDiagrama>();
  readonly gateway = input<GatewayRespuesta | null>(null);
  readonly propuesta = input<PropuestaGateway | null>(null);
  readonly guardado = output<GatewayRespuesta>();
  readonly cancelado = output<void>();

  protected readonly id = `formulario-gateway-${++siguienteFormulario}`;
  protected readonly tipos = TIPOS_GATEWAY;
  protected readonly textoTipo = TEXTO_TIPO_GATEWAY;
  protected readonly limite = LONGITUD_MAXIMA_CONDICION;
  protected readonly edicion = computed(() => this.gateway() !== null);
  protected readonly pools = computed(() =>
    [...this.diagrama().pools].filter((pool) => !pool.cajaNegra).sort((uno, otro) => uno.orden - otro.orden),
  );

  protected readonly formulario = new FormGroup({
    tipo: new FormControl<TipoGateway | null>(null, { validators: [Validators.required] }),
    poolId: new FormControl<number | null>(null, { validators: [Validators.required] }),
    posicionX: new FormControl<number | null>(null, { validators: [Validators.required, entero] }),
    posicionY: new FormControl<number | null>(null, { validators: [Validators.required, entero] }),
    condiciones: new FormRecord<FormControl<string>>({}),
  });

  protected readonly salidas = signal<ArcoRespuesta[]>([]);
  protected readonly tipoElegido = signal<TipoGateway | null>(null);
  protected readonly conCondiciones = computed(() => {
    const tipo = this.tipoElegido();
    return tipo !== null && exigeCondicion(tipo);
  });
  protected readonly guardando = signal(false);
  protected readonly fallo = signal<FalloHttp | null>(null);

  constructor() {
    effect(() => {
      const gateway = this.gateway();
      const propuesta = this.propuesta();
      untracked(() => this.preparar(gateway, propuesta));
    });
    const { tipo, poolId } = this.formulario.controls;
    tipo.valueChanges.pipe(takeUntilDestroyed()).subscribe((valor) => this.aplicarTipo(valor));
    poolId.valueChanges.pipe(takeUntilDestroyed()).subscribe((valor) => this.ajustarPosicion(valor));
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
    return control.hasError('entero') ? 'Usa un número entero' : OBLIGATORIO[campo];
  }

  protected errorCondicion(arco: ArcoRespuesta): string | null {
    const control = this.formulario.controls.condiciones.get(String(arco.id));
    if (!control || control.disabled || control.valid || !control.touched) {
      return null;
    }
    return control.hasError('maxlength')
      ? `La condición no puede superar ${this.limite} caracteres`
      : 'Cada salida de un gateway exclusivo o inclusivo necesita condición';
  }

  protected guardar(): void {
    if (this.guardando()) {
      return;
    }
    this.fallo.set(null);
    const { tipo, poolId, posicionX, posicionY, condiciones } = this.formulario.getRawValue();
    if (this.formulario.invalid || tipo === null) {
      this.formulario.markAllAsTouched();
      return;
    }
    const solicitud = this.enviar(tipo, poolId, posicionX, posicionY, condiciones);
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

  private enviar(
    tipo: TipoGateway,
    poolId: number | null,
    posicionX: number | null,
    posicionY: number | null,
    condiciones: Record<string, string>,
  ): Observable<GatewayRespuesta> | null {
    const actual = this.gateway();
    if (actual) {
      const textos = exigeCondicion(tipo)
        ? Object.fromEntries(Object.entries(condiciones).map(([arco, texto]) => [arco, texto.trim()]))
        : {};
      return this.gateways.editar(this.procesoId(), actual.id, { tipo, condiciones: textos });
    }
    return posicionX === null || posicionY === null
      ? null
      : this.gateways.crear(this.procesoId(), { tipo, posicionX, posicionY, poolId });
  }

  private preparar(gateway: GatewayRespuesta | null, propuesta: PropuestaGateway | null): void {
    this.fallo.set(null);
    const { poolId, posicionX, posicionY } = this.formulario.controls;
    const condiciones = new FormRecord<FormControl<string>>({});
    const salidas = gateway
      ? this.diagrama().arcos.filter((arco) => arco.origenTipo === 'GATEWAY' && arco.origenId === gateway.id)
      : [];
    salidas.forEach((arco) =>
      condiciones.addControl(String(arco.id), new FormControl(arco.condicion ?? '', { nonNullable: true })),
    );
    this.formulario.setControl('condiciones', condiciones, { emitEvent: false });
    this.salidas.set(salidas);
    if (gateway) {
      this.formulario.patchValue(
        { tipo: gateway.tipo, poolId: gateway.poolId, posicionX: gateway.posicionX, posicionY: gateway.posicionY },
        { emitEvent: false },
      );
      [poolId, posicionX, posicionY].forEach((control) => control.disable({ emitEvent: false }));
    } else {
      [poolId, posicionX, posicionY].forEach((control) => control.enable({ emitEvent: false }));
      this.formulario.patchValue(
        {
          poolId: propuesta?.poolId ?? this.pools()[0]?.id ?? null,
          posicionX: propuesta?.posicion.x ?? null,
          posicionY: propuesta?.posicion.y ?? null,
        },
        { emitEvent: false },
      );
    }
    this.aplicarTipo(this.formulario.controls.tipo.value);
  }

  private aplicarTipo(tipo: TipoGateway | null): void {
    this.tipoElegido.set(tipo);
    const activas = tipo !== null && exigeCondicion(tipo);
    Object.values(this.formulario.controls.condiciones.controls).forEach((control) => {
      control.setValidators(
        activas ? [Validators.required, noEnBlanco, Validators.maxLength(LONGITUD_MAXIMA_CONDICION)] : [],
      );
      if (activas) {
        control.enable({ emitEvent: false });
      } else {
        control.disable({ emitEvent: false });
      }
      control.updateValueAndValidity({ emitEvent: false });
    });
  }

  private ajustarPosicion(poolId: number | null): void {
    const geometria = this.geometria();
    const banda = poolId === null || this.edicion() ? null : bandaDePool(geometria, poolId);
    const { posicionX, posicionY } = this.formulario.getRawValue();
    if (!banda || posicionX === null || posicionY === null) {
      return;
    }
    const posicion = colocarEnBanda('GATEWAY', centro(cajaDeNodo('GATEWAY', posicionX, posicionY)), banda, geometria.inicioContenido);
    this.formulario.patchValue({ posicionX: posicion.x, posicionY: posicion.y }, { emitEvent: false });
  }
}

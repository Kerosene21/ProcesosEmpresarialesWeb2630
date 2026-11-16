import { ChangeDetectionStrategy, Component, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';

import { Aviso } from '../../compartido/aviso/aviso';
import { aplicarErroresDelServidor, errorDelServidor } from '../../core/formularios/errores-servidor';
import { noEnBlanco } from '../../core/formularios/validadores';
import { LONGITUD_MAXIMA_ARCO } from '../../core/modelos/arco';
import { ArcoRespuesta, DiagramaProceso } from '../../core/modelos/diagrama';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { Seleccion } from '../../core/modelos/seleccion';
import { ArcoService } from '../../core/servicios/arco-service';
import { arcoEntre, destinosPermitidos } from '../conexiones';
import { GeometriaDiagrama, NodoDiagrama, buscarNodo, claveNodo } from '../geometria';

export interface PropuestaArco {
  origen: Seleccion | null;
  destino: Seleccion | null;
}

type Campo = 'origen' | 'destino' | 'etiqueta' | 'condicion';

const OBLIGATORIO: Record<Campo, string> = {
  origen: 'El nodo origen es obligatorio',
  destino: 'El nodo destino es obligatorio',
  etiqueta: '',
  condicion: 'Un arco que sale de un gateway exclusivo o inclusivo necesita condición',
};

const LONGITUD: Partial<Record<Campo, string>> = {
  etiqueta: `La etiqueta no puede superar ${LONGITUD_MAXIMA_ARCO.etiqueta} caracteres`,
  condicion: `La condición no puede superar ${LONGITUD_MAXIMA_ARCO.condicion} caracteres`,
};

const CAMPOS_POR_CODIGO: Readonly<Record<string, Campo>> = {
  ARCO_DUPLICADO: 'destino',
  SECUENCIA_ENTRE_POOLS: 'destino',
  CATCH_INICIO_CON_ENTRADA: 'destino',
  CONDICION_NO_VALIDA: 'condicion',
};

const VALIDADORES_CONDICION = [Validators.required, noEnBlanco, Validators.maxLength(LONGITUD_MAXIMA_ARCO.condicion)];

let siguienteFormulario = 0;

@Component({
  selector: 'app-formulario-arco',
  imports: [ReactiveFormsModule, Aviso],
  templateUrl: './formulario-arco.html',
  styleUrl: './formulario-arco.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FormularioArco {
  private readonly arcos = inject(ArcoService);

  readonly procesoId = input.required<number>();
  readonly diagrama = input.required<DiagramaProceso>();
  readonly geometria = input.required<GeometriaDiagrama>();
  readonly arco = input<ArcoRespuesta | null>(null);
  readonly propuesta = input<PropuestaArco | null>(null);
  readonly guardado = output<ArcoRespuesta>();
  readonly cancelado = output<void>();

  protected readonly id = `formulario-arco-${++siguienteFormulario}`;
  protected readonly limites = LONGITUD_MAXIMA_ARCO;
  protected readonly clave = (nodo: NodoDiagrama) => claveNodo(nodo.tipoFlujo, nodo.elemento.id);
  protected readonly edicion = computed(() => this.arco() !== null);

  protected readonly formulario = new FormGroup({
    origen: new FormControl<string | null>(null, { validators: [Validators.required] }),
    destino: new FormControl<string | null>(null, { validators: [Validators.required] }),
    etiqueta: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(LONGITUD_MAXIMA_ARCO.etiqueta)] }),
    condicion: new FormControl('', { nonNullable: true }),
  });

  private readonly claveOrigen = signal<string | null>(null);
  private readonly claveDestino = signal<string | null>(null);
  protected readonly nodos = computed(() => this.geometria().nodos);
  protected readonly origen = computed(() => this.nodo(this.claveOrigen()));
  protected readonly destino = computed(() => this.nodo(this.claveDestino()));
  protected readonly destinos = computed(() => {
    const origen = this.origen();
    return origen ? destinosPermitidos(this.nodos(), origen) : [];
  });
  protected readonly conCondicion = computed(() => this.origen()?.exigeCondicion === true);
  protected readonly duplicado = computed(() => {
    const origen = this.origen();
    const destino = this.destino();
    return origen && destino ? arcoEntre(this.diagrama().arcos, origen, destino, this.arco()?.id ?? null) : null;
  });
  protected readonly guardando = signal(false);
  protected readonly fallo = signal<FalloHttp | null>(null);

  constructor() {
    effect(() => {
      const arco = this.arco();
      const propuesta = this.propuesta();
      untracked(() => this.preparar(arco, propuesta));
    });
    const { origen, destino } = this.formulario.controls;
    origen.valueChanges.pipe(takeUntilDestroyed()).subscribe((valor) => this.aplicarOrigen(valor));
    destino.valueChanges.pipe(takeUntilDestroyed()).subscribe((valor) => this.claveDestino.set(valor));
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
    return control.hasError('maxlength') ? (LONGITUD[campo] ?? null) : OBLIGATORIO[campo];
  }

  protected guardar(): void {
    if (this.guardando()) {
      return;
    }
    this.fallo.set(null);
    const origen = this.origen();
    const destino = this.destino();
    if (this.formulario.invalid || !origen || !destino || this.duplicado()) {
      this.formulario.markAllAsTouched();
      return;
    }
    const { etiqueta, condicion } = this.formulario.getRawValue();
    const datos = {
      origenTipo: origen.tipoFlujo,
      origenId: origen.elemento.id,
      destinoTipo: destino.tipoFlujo,
      destinoId: destino.elemento.id,
      etiqueta: textoONulo(etiqueta),
      condicion: this.conCondicion() ? textoONulo(condicion) : null,
    };
    const actual = this.arco();
    const solicitud = actual ? this.arcos.editar(this.procesoId(), actual.id, datos) : this.arcos.crear(this.procesoId(), datos);
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

  private preparar(arco: ArcoRespuesta | null, propuesta: PropuestaArco | null): void {
    this.fallo.set(null);
    const geometria = this.geometria();
    const origen = arco
      ? claveNodo(arco.origenTipo, arco.origenId)
      : this.claveDe(buscarNodo(geometria, propuesta?.origen ?? null));
    const destino = arco
      ? claveNodo(arco.destinoTipo, arco.destinoId)
      : this.claveDe(buscarNodo(geometria, propuesta?.destino ?? null));
    this.formulario.patchValue({ origen, etiqueta: arco?.etiqueta ?? '', condicion: arco?.condicion ?? '' }, { emitEvent: false });
    this.aplicarOrigen(origen);
    if (destino !== null && this.destinos().some((nodo) => this.clave(nodo) === destino)) {
      this.formulario.controls.destino.setValue(destino, { emitEvent: false });
      this.claveDestino.set(destino);
    }
  }

  private aplicarOrigen(origen: string | null): void {
    this.claveOrigen.set(origen);
    const { destino, condicion } = this.formulario.controls;
    if (destino.value !== null && !this.destinos().some((nodo) => this.clave(nodo) === destino.value)) {
      destino.setValue(null, { emitEvent: false });
    }
    this.claveDestino.set(destino.value);
    if (this.conCondicion()) {
      condicion.setValidators(VALIDADORES_CONDICION);
      condicion.enable({ emitEvent: false });
    } else {
      condicion.clearValidators();
      condicion.disable({ emitEvent: false });
    }
    condicion.updateValueAndValidity({ emitEvent: false });
  }

  private nodo(clave: string | null): NodoDiagrama | null {
    return clave === null ? null : (this.nodos().find((nodo) => this.clave(nodo) === clave) ?? null);
  }

  private claveDe(nodo: NodoDiagrama | null): string | null {
    return nodo ? this.clave(nodo) : null;
  }
}

function textoONulo(texto: string): string | null {
  const limpio = texto.trim();
  return limpio.length > 0 ? limpio : null;
}

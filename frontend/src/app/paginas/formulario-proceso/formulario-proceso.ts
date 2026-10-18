import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable, of, switchMap } from 'rxjs';

import { Aviso } from '../../compartido/aviso/aviso';
import { TEXTO_ESTADO, explicarFallo } from '../../compartido/textos';
import { noEnBlanco } from '../../core/formularios/validadores';
import { EstadoCarga } from '../../core/modelos/estado-carga';
import { ESTADOS_PROCESO, EstadoProceso, LONGITUD_MAXIMA_PROCESO, Proceso } from '../../core/modelos/proceso';
import { conEstadoCarga } from '../../core/rxjs/con-estado-carga';
import { ProcesoService } from '../../core/servicios/proceso-service';

type Campo = 'nombre' | 'descripcion' | 'categoria' | 'estado';

const MENSAJES: Record<Campo, { obligatorio: string; longitud?: string }> = {
  nombre: {
    obligatorio: 'El nombre es obligatorio',
    longitud: `El nombre no puede superar ${LONGITUD_MAXIMA_PROCESO.nombre} caracteres`,
  },
  descripcion: { obligatorio: 'La descripción es obligatoria' },
  categoria: {
    obligatorio: 'La categoría es obligatoria',
    longitud: `La categoría no puede superar ${LONGITUD_MAXIMA_PROCESO.categoria} caracteres`,
  },
  estado: { obligatorio: 'El estado es obligatorio' },
};

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
  selector: 'app-formulario-proceso',
  imports: [ReactiveFormsModule, RouterLink, Aviso],
  templateUrl: './formulario-proceso.html',
  styleUrl: './formulario-proceso.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FormularioProceso {
  private readonly procesos = inject(ProcesoService);

  readonly id = input<string>();

  protected readonly limites = LONGITUD_MAXIMA_PROCESO;
  protected readonly estados = ESTADOS_PROCESO;
  protected readonly textoEstado = TEXTO_ESTADO;
  protected readonly explicar = explicarFallo;

  protected readonly formulario = new FormGroup({
    nombre: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, noEnBlanco, Validators.maxLength(LONGITUD_MAXIMA_PROCESO.nombre)],
    }),
    descripcion: new FormControl('', { nonNullable: true, validators: [Validators.required, noEnBlanco] }),
    categoria: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, noEnBlanco, Validators.maxLength(LONGITUD_MAXIMA_PROCESO.categoria)],
    }),
    estado: new FormControl<EstadoProceso>('BORRADOR', { nonNullable: true, validators: [Validators.required] }),
  });

  protected readonly modoEdicion = computed(() => this.id() !== undefined);
  protected readonly carga = signal<EstadoCarga<Proceso> | null>(null);
  protected readonly revision = signal<'valida' | 'con-errores' | null>(null);
  private readonly intento = signal(0);

  protected readonly proceso = computed(() => {
    const carga = this.carga();
    return carga?.tipo === 'listo' ? carga.datos : null;
  });
  protected readonly fallo = computed(() => {
    const carga = this.carga();
    return carga?.tipo === 'error' ? carga.fallo : null;
  });
  protected readonly soloConsulta = computed(() => {
    const proceso = this.proceso();
    return proceso !== null && (proceso.soloLectura || proceso.eliminado);
  });
  protected readonly mostrarFormulario = computed(() => !this.modoEdicion() || this.proceso() !== null);
  protected readonly rutaCancelar = computed(() => (this.modoEdicion() ? ['/procesos', this.id()] : ['/procesos']));

  constructor() {
    toObservable(computed(() => ({ id: this.id(), intento: this.intento() })))
      .pipe(
        switchMap(({ id }) => this.cargar(id)),
        takeUntilDestroyed(),
      )
      .subscribe((carga) => this.aplicar(carga));

    this.formulario.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.revision.set(null));
  }

  protected error(campo: Campo): string | null {
    const control = this.formulario.controls[campo];
    if (control.disabled || control.valid || !(control.touched || this.revision() !== null)) {
      return null;
    }
    if (control.hasError('maxlength')) {
      return MENSAJES[campo].longitud ?? null;
    }
    return MENSAJES[campo].obligatorio;
  }

  protected longitud(campo: 'nombre' | 'categoria'): number {
    return this.formulario.controls[campo].value.length;
  }

  protected revisar(): void {
    this.formulario.markAllAsTouched();
    this.revision.set(this.formulario.valid ? 'valida' : 'con-errores');
  }

  protected reintentar(): void {
    this.intento.update((intento) => intento + 1);
  }

  private cargar(id: string | undefined): Observable<EstadoCarga<Proceso> | null> {
    if (id === undefined) {
      return of(null);
    }
    const numero = Number(id);
    return Number.isInteger(numero) && numero > 0
      ? this.procesos.obtener(numero).pipe(conEstadoCarga())
      : of(IDENTIFICADOR_NO_VALIDO);
  }

  private aplicar(carga: EstadoCarga<Proceso> | null): void {
    this.carga.set(carga);
    if (carga === null) {
      this.formulario.controls.estado.disable({ emitEvent: false });
      return;
    }
    if (carga.tipo !== 'listo') {
      return;
    }
    const { nombre, descripcion, categoria, estado } = carga.datos;
    this.formulario.setValue({ nombre, descripcion, categoria, estado }, { emitEvent: false });
    if (this.soloConsulta()) {
      this.formulario.disable({ emitEvent: false });
    }
  }
}

import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Observable, of, switchMap } from 'rxjs';

import { Aviso } from '../../compartido/aviso/aviso';
import { explicarFallo } from '../../compartido/textos';
import { aplicarErroresDelServidor, errorDelServidor } from '../../core/formularios/errores-servidor';
import { noEnBlanco } from '../../core/formularios/validadores';
import { EstadoCarga } from '../../core/modelos/estado-carga';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { LONGITUD_MAXIMA_ROL, RolProcesoResumen } from '../../core/modelos/rol-proceso';
import { conEstadoCarga } from '../../core/rxjs/con-estado-carga';
import { RolProcesoService } from '../../core/servicios/rol-proceso-service';

type Campo = 'nombre' | 'descripcion';

const MENSAJES: Record<Campo, { obligatorio: string; longitud: string }> = {
  nombre: {
    obligatorio: 'El nombre es obligatorio',
    longitud: `El nombre no puede superar ${LONGITUD_MAXIMA_ROL.nombre} caracteres`,
  },
  descripcion: {
    obligatorio: 'La descripción es obligatoria',
    longitud: `La descripción no puede superar ${LONGITUD_MAXIMA_ROL.descripcion} caracteres`,
  },
};

const CAMPOS_POR_CONFLICTO: Readonly<Record<string, Campo>> = {
  ROL_PROCESO_NOMBRE_DUPLICADO: 'nombre',
};

const IDENTIFICADOR_NO_VALIDO: EstadoCarga<never> = {
  tipo: 'error',
  fallo: {
    tipo: 'no-encontrado',
    estado: 0,
    codigo: null,
    titulo: 'El identificador del rol de proceso no es válido',
    detalle: null,
  },
};

@Component({
  selector: 'app-formulario-rol',
  imports: [ReactiveFormsModule, RouterLink, Aviso],
  templateUrl: './formulario-rol.html',
  styleUrl: './formulario-rol.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FormularioRol {
  private readonly roles = inject(RolProcesoService);
  private readonly router = inject(Router);

  readonly id = input<string>();

  protected readonly limites = LONGITUD_MAXIMA_ROL;
  protected readonly explicar = explicarFallo;

  protected readonly formulario = new FormGroup({
    nombre: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, noEnBlanco, Validators.maxLength(LONGITUD_MAXIMA_ROL.nombre)],
    }),
    descripcion: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, noEnBlanco, Validators.maxLength(LONGITUD_MAXIMA_ROL.descripcion)],
    }),
  });

  protected readonly modoEdicion = computed(() => this.id() !== undefined);
  protected readonly carga = signal<EstadoCarga<RolProcesoResumen> | null>(null);
  protected readonly guardando = signal(false);
  protected readonly conErrores = signal(false);
  protected readonly falloGuardado = signal<FalloHttp | null>(null);
  private readonly intento = signal(0);

  protected readonly rol = computed(() => {
    const carga = this.carga();
    return carga?.tipo === 'listo' ? carga.datos : null;
  });
  protected readonly fallo = computed(() => {
    const carga = this.carga();
    return carga?.tipo === 'error' ? carga.fallo : null;
  });
  protected readonly eliminado = computed(() => this.rol()?.activo === false);
  protected readonly mostrarFormulario = computed(() => !this.modoEdicion() || this.rol() !== null);
  protected readonly rutaCancelar = computed(() =>
    this.modoEdicion() ? ['/roles-proceso', this.id()] : ['/roles-proceso'],
  );

  constructor() {
    toObservable(computed(() => ({ id: this.id(), intento: this.intento() })))
      .pipe(
        switchMap(({ id }) => this.cargar(id)),
        takeUntilDestroyed(),
      )
      .subscribe((carga) => this.aplicar(carga));

    this.formulario.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.conErrores.set(false));
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
    return control.hasError('maxlength') ? MENSAJES[campo].longitud : MENSAJES[campo].obligatorio;
  }

  protected longitud(campo: Campo): number {
    return this.formulario.controls[campo].value.length;
  }

  protected guardar(): void {
    this.falloGuardado.set(null);
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      this.conErrores.set(true);
      return;
    }
    this.guardando.set(true);
    const datos = this.formulario.getRawValue();
    const id = this.id();
    const solicitud = id === undefined ? this.roles.crear(datos) : this.roles.editar(Number(id), datos);
    solicitud.subscribe({
      next: (guardado) => void this.router.navigate(['/roles-proceso', guardado.id]),
      error: (fallo: FalloHttp) => {
        this.guardando.set(false);
        if (aplicarErroresDelServidor(this.formulario, fallo, CAMPOS_POR_CONFLICTO)) {
          this.conErrores.set(true);
        } else {
          this.falloGuardado.set(fallo);
        }
      },
    });
  }

  protected reintentar(): void {
    this.intento.update((intento) => intento + 1);
  }

  private cargar(id: string | undefined): Observable<EstadoCarga<RolProcesoResumen> | null> {
    if (id === undefined) {
      return of(null);
    }
    const numero = Number(id);
    return Number.isInteger(numero) && numero > 0
      ? this.roles.obtener(numero).pipe(conEstadoCarga())
      : of(IDENTIFICADOR_NO_VALIDO);
  }

  private aplicar(carga: EstadoCarga<RolProcesoResumen> | null): void {
    this.carga.set(carga);
    if (carga?.tipo !== 'listo') {
      return;
    }
    const { nombre, descripcion } = carga.datos;
    this.formulario.setValue({ nombre, descripcion }, { emitEvent: false });
    if (!carga.datos.activo) {
      this.formulario.disable({ emitEvent: false });
    }
  }
}

import { ChangeDetectionStrategy, Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { switchMap } from 'rxjs';

import { Aviso } from '../../compartido/aviso/aviso';
import { DialogoConfirmacion } from '../../compartido/dialogo-confirmacion/dialogo-confirmacion';
import { explicarFallo } from '../../compartido/textos';
import { aplicarErroresDelServidor, errorDelServidor } from '../../core/formularios/errores-servidor';
import { entero } from '../../core/formularios/validadores';
import { EmpresaInvitada } from '../../core/modelos/comparticion';
import { CARGANDO, EstadoCarga } from '../../core/modelos/estado-carga';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { Proceso } from '../../core/modelos/proceso';
import { conEstadoCarga } from '../../core/rxjs/con-estado-carga';
import { ComparticionService } from '../../core/servicios/comparticion-service';
import { SesionService } from '../../core/servicios/sesion-service';

const CAMPOS_POR_CODIGO: Readonly<Record<string, string>> = {
  COMPARTICION_NO_VALIDA: 'empresaId',
  PROCESO_YA_COMPARTIDO: 'empresaId',
};

@Component({
  selector: 'app-panel-comparticion',
  imports: [ReactiveFormsModule, Aviso, DialogoConfirmacion],
  templateUrl: './panel-comparticion.html',
  styleUrl: './panel-comparticion.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PanelComparticion {
  private readonly comparticiones = inject(ComparticionService);
  private readonly sesion = inject(SesionService);

  readonly proceso = input.required<Proceso>();

  protected readonly explicar = explicarFallo;
  private readonly consulta = signal(0);
  protected readonly estado = toSignal(
    toObservable(computed(() => ({ id: this.proceso().id, consulta: this.consulta() }))).pipe(
      switchMap(({ id }) => this.comparticiones.listar(id).pipe(conEstadoCarga())),
    ),
    { initialValue: CARGANDO as EstadoCarga<EmpresaInvitada[]> },
  );
  protected readonly empresas = linkedSignal<EstadoCarga<EmpresaInvitada[]>, EmpresaInvitada[] | null>({
    source: this.estado,
    computation: (estado, previo) =>
      estado.tipo === 'listo' ? estado.datos : estado.tipo === 'cargando' ? (previo?.value ?? null) : null,
  });
  protected readonly falloConsulta = computed(() => {
    const estado = this.estado();
    return estado.tipo === 'error' ? estado.fallo : null;
  });
  protected readonly esAdministrador = computed(() => this.sesion.tieneRol(['ADMINISTRADOR']));
  protected readonly gestionable = computed(() => this.esAdministrador() && !this.proceso().eliminado);

  protected readonly formulario = new FormGroup({
    empresaId: new FormControl<number | null>(null, { validators: [Validators.required, entero, Validators.min(1)] }),
  });
  protected readonly procesando = signal(false);
  protected readonly mensaje = signal<string | null>(null);
  protected readonly fallo = signal<FalloHttp | null>(null);
  protected readonly porRetirar = signal<EmpresaInvitada | null>(null);
  protected readonly falloRetiro = signal<string | null>(null);

  protected compartir(): void {
    this.limpiarMensajes();
    const { empresaId } = this.formulario.getRawValue();
    const control = this.formulario.controls.empresaId;
    if (this.formulario.invalid || empresaId === null) {
      this.formulario.markAllAsTouched();
      return;
    }
    if (this.empresas()?.some((empresa) => empresa.id === empresaId)) {
      control.setErrors({ servidor: 'El proceso ya está compartido con esa empresa' });
      control.markAsTouched();
      return;
    }
    if (this.procesando()) {
      return;
    }
    this.procesando.set(true);
    this.comparticiones.compartir(this.proceso().id, empresaId).subscribe({
      next: (empresa) => {
        this.procesando.set(false);
        this.formulario.reset();
        this.mensaje.set(`El proceso se compartió en solo lectura con «${empresa.nombre}». Quedó registrado en el historial.`);
        this.recargar();
      },
      error: (fallo: FalloHttp) => {
        this.procesando.set(false);
        if (!aplicarErroresDelServidor(this.formulario, fallo, CAMPOS_POR_CODIGO)) {
          this.fallo.set(fallo);
        }
      },
    });
  }

  protected solicitarRetiro(empresa: EmpresaInvitada): void {
    this.limpiarMensajes();
    this.falloRetiro.set(null);
    this.porRetirar.set(empresa);
  }

  protected cancelarRetiro(): void {
    this.porRetirar.set(null);
    this.falloRetiro.set(null);
  }

  protected retirar(): void {
    const empresa = this.porRetirar();
    if (!empresa || this.procesando()) {
      return;
    }
    this.procesando.set(true);
    this.falloRetiro.set(null);
    this.comparticiones.dejarDeCompartir(this.proceso().id, empresa.id).subscribe({
      next: () => {
        this.procesando.set(false);
        this.porRetirar.set(null);
        this.mensaje.set(`«${empresa.nombre}» ya no puede consultar el proceso. Quedó registrado en el historial.`);
        this.recargar();
      },
      error: (fallo: FalloHttp) => {
        this.procesando.set(false);
        this.falloRetiro.set(fallo.detalle ?? fallo.titulo);
      },
    });
  }

  protected recargar(): void {
    this.consulta.update((consulta) => consulta + 1);
  }

  protected error(): string | null {
    const control = this.formulario.controls.empresaId;
    if (control.valid || !control.touched) {
      return null;
    }
    const servidor = errorDelServidor(control);
    if (servidor) {
      return servidor;
    }
    return control.hasError('required')
      ? 'Indica el identificador de la empresa'
      : 'Usa un identificador entero mayor que cero';
  }

  private limpiarMensajes(): void {
    this.mensaje.set(null);
    this.fallo.set(null);
  }
}

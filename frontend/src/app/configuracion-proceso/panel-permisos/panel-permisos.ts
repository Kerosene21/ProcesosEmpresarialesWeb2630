import { ChangeDetectionStrategy, Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, FormRecord, ReactiveFormsModule } from '@angular/forms';
import { forkJoin, switchMap } from 'rxjs';

import { Aviso } from '../../compartido/aviso/aviso';
import { TEXTO_ROL, explicarFallo } from '../../compartido/textos';
import { CARGANDO, EstadoCarga } from '../../core/modelos/estado-carga';
import { FalloHttp } from '../../core/modelos/fallo-http';
import {
  ConfigurarPermisoEstructura,
  OPERACIONES_ESTRUCTURA,
  OperacionEstructura,
  PermisoEstructura,
  operacionesDe,
} from '../../core/modelos/permiso-estructura';
import { Proceso } from '../../core/modelos/proceso';
import { conEstadoCarga } from '../../core/rxjs/con-estado-carga';
import { ProcesoService } from '../../core/servicios/proceso-service';
import { SesionService } from '../../core/servicios/sesion-service';

type FilaPermisos = FormGroup<Record<OperacionEstructura, FormControl<boolean>>>;

function filaDe(permiso: PermisoEstructura): FilaPermisos {
  const valores = operacionesDe(permiso);
  return new FormGroup(
    Object.fromEntries(
      OPERACIONES_ESTRUCTURA.map(({ clave }) => [clave, new FormControl(valores[clave], { nonNullable: true })]),
    ) as Record<OperacionEstructura, FormControl<boolean>>,
  );
}

function formularioDe(matriz: PermisoEstructura[] | null, configurable: boolean): FormRecord<FilaPermisos> {
  const formulario = new FormRecord<FilaPermisos>({});
  (matriz ?? []).forEach((permiso) => {
    const fila = filaDe(permiso);
    if (!configurable || !permiso.configurable) {
      fila.disable();
    }
    formulario.addControl(permiso.rol, fila);
  });
  return formulario;
}

@Component({
  selector: 'app-panel-permisos',
  imports: [ReactiveFormsModule, Aviso],
  templateUrl: './panel-permisos.html',
  styleUrl: './panel-permisos.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PanelPermisos {
  private readonly procesos = inject(ProcesoService);
  private readonly sesion = inject(SesionService);

  readonly proceso = input.required<Proceso>();

  protected readonly operaciones = OPERACIONES_ESTRUCTURA;
  protected readonly textoRol = TEXTO_ROL;
  protected readonly explicar = explicarFallo;
  private readonly consulta = signal(0);
  protected readonly estado = toSignal(
    toObservable(computed(() => ({ id: this.proceso().id, consulta: this.consulta() }))).pipe(
      switchMap(({ id }) => this.procesos.permisosEstructura(id).pipe(conEstadoCarga())),
    ),
    { initialValue: CARGANDO as EstadoCarga<PermisoEstructura[]> },
  );
  protected readonly matriz = linkedSignal<EstadoCarga<PermisoEstructura[]>, PermisoEstructura[] | null>({
    source: this.estado,
    computation: (estado, previo) =>
      estado.tipo === 'listo' ? estado.datos : estado.tipo === 'cargando' ? (previo?.value ?? null) : null,
  });
  protected readonly falloConsulta = computed(() => {
    const estado = this.estado();
    return estado.tipo === 'error' ? estado.fallo : null;
  });
  protected readonly configurable = computed(() => this.sesion.tieneRol(['ADMINISTRADOR']) && !this.proceso().eliminado);

  private readonly reinicio = signal(0);
  protected readonly formulario = computed(() => {
    this.reinicio();
    return formularioDe(this.matriz(), this.configurable());
  });
  protected readonly cambios = linkedSignal(() => this.formulario().dirty);
  protected readonly guardando = signal(false);
  protected readonly mensaje = signal<string | null>(null);
  protected readonly fallo = signal<FalloHttp | null>(null);

  protected marcar(): void {
    this.cambios.set(this.formulario().dirty);
    this.mensaje.set(null);
  }

  protected restaurar(): void {
    this.fallo.set(null);
    this.mensaje.set(null);
    this.reinicio.update((reinicio) => reinicio + 1);
  }

  protected guardar(): void {
    const matriz = this.matriz();
    const formulario = this.formulario();
    if (!matriz || this.guardando() || !formulario.dirty) {
      return;
    }
    const solicitudes = matriz
      .filter((permiso) => permiso.configurable && formulario.controls[permiso.rol]?.dirty)
      .map((permiso) =>
        this.procesos.configurarPermisosEstructura(
          this.proceso().id,
          permiso.rol,
          formulario.controls[permiso.rol].getRawValue() as ConfigurarPermisoEstructura,
        ),
      );
    if (solicitudes.length === 0) {
      return;
    }
    this.fallo.set(null);
    this.mensaje.set(null);
    this.guardando.set(true);
    forkJoin(solicitudes).subscribe({
      next: () => {
        this.guardando.set(false);
        this.mensaje.set('Los permisos se guardaron. Se aplican a las próximas operaciones sobre pools y lanes.');
        this.consulta.update((consulta) => consulta + 1);
      },
      error: (fallo: FalloHttp) => {
        this.guardando.set(false);
        this.fallo.set(fallo);
      },
    });
  }

  protected recargar(): void {
    this.consulta.update((consulta) => consulta + 1);
  }
}

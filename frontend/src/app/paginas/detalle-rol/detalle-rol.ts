import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Observable, of, switchMap } from 'rxjs';

import { Aviso } from '../../compartido/aviso/aviso';
import { DialogoConfirmacion } from '../../compartido/dialogo-confirmacion/dialogo-confirmacion';
import { TEXTO_ACCION_ROL, explicarFallo } from '../../compartido/textos';
import { CARGANDO, EstadoCarga } from '../../core/modelos/estado-carga';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { HistorialRolProceso, RolProcesoResumen } from '../../core/modelos/rol-proceso';
import { ROLES_ESCRITURA } from '../../core/modelos/sesion';
import { conEstadoCarga } from '../../core/rxjs/con-estado-carga';
import { RolProcesoService } from '../../core/servicios/rol-proceso-service';
import { SesionService } from '../../core/servicios/sesion-service';

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
  selector: 'app-detalle-rol',
  imports: [DatePipe, RouterLink, Aviso, DialogoConfirmacion],
  templateUrl: './detalle-rol.html',
  styleUrl: './detalle-rol.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetalleRol {
  private readonly roles = inject(RolProcesoService);
  private readonly sesion = inject(SesionService);

  readonly id = input.required<string>();

  protected readonly explicar = explicarFallo;
  protected readonly textoAccion = TEXTO_ACCION_ROL;
  private readonly intento = signal(0);
  private readonly solicitud$ = toObservable(computed(() => ({ id: Number(this.id()), intento: this.intento() })));

  protected readonly estadoRol = toSignal(
    this.solicitud$.pipe(switchMap(({ id }) => this.cargar(id, (valido) => this.roles.obtener(valido)))),
    { initialValue: CARGANDO as EstadoCarga<RolProcesoResumen> },
  );
  protected readonly estadoHistorial = toSignal(
    this.solicitud$.pipe(switchMap(({ id }) => this.cargar(id, (valido) => this.roles.historial(valido)))),
    { initialValue: CARGANDO as EstadoCarga<HistorialRolProceso[]> },
  );

  protected readonly rol = computed(() => {
    const estado = this.estadoRol();
    return estado.tipo === 'listo' ? estado.datos : null;
  });
  protected readonly falloRol = computed(() => {
    const estado = this.estadoRol();
    return estado.tipo === 'error' ? estado.fallo : null;
  });
  protected readonly historial = computed(() => {
    const estado = this.estadoHistorial();
    return estado.tipo === 'listo' ? estado.datos : null;
  });
  protected readonly falloHistorial = computed(() => {
    const estado = this.estadoHistorial();
    return estado.tipo === 'error' ? estado.fallo : null;
  });

  protected readonly puedeEditar = computed(() => this.rol()?.activo === true && this.sesion.tieneRol(ROLES_ESCRITURA));
  protected readonly esAdministrador = computed(() => this.sesion.tieneRol(['ADMINISTRADOR']));
  protected readonly bloqueadoPorUso = computed(() => {
    const rol = this.rol();
    return rol !== null && rol.activo && rol.enUso;
  });

  protected readonly preparando = signal(false);
  protected readonly confirmando = signal(false);
  protected readonly eliminando = signal(false);
  protected readonly falloEliminacion = signal<string | null>(null);
  protected readonly eliminado = signal(false);
  protected readonly usoDetectado = signal<RolProcesoResumen | null>(null);
  protected readonly procesosEnUso = computed(
    () => this.usoDetectado()?.procesos.map((proceso) => proceso.nombre).join(', ') ?? '',
  );

  protected solicitarEliminacion(): void {
    const rol = this.rol();
    if (!rol) {
      return;
    }
    this.preparando.set(true);
    this.falloEliminacion.set(null);
    this.usoDetectado.set(null);
    this.roles.previsualizarEliminacion(rol.id).subscribe({
      next: (vista) => {
        this.preparando.set(false);
        if (vista.enUso) {
          this.usoDetectado.set(vista);
        } else {
          this.confirmando.set(true);
        }
      },
      error: (fallo: FalloHttp) => {
        this.preparando.set(false);
        this.falloEliminacion.set(fallo.detalle ?? fallo.titulo);
      },
    });
  }

  protected cancelarEliminacion(): void {
    this.confirmando.set(false);
    this.falloEliminacion.set(null);
  }

  protected eliminar(): void {
    const rol = this.rol();
    if (!rol) {
      return;
    }
    this.eliminando.set(true);
    this.falloEliminacion.set(null);
    this.roles.eliminar(rol.id).subscribe({
      next: () => {
        this.eliminando.set(false);
        this.confirmando.set(false);
        this.eliminado.set(true);
        this.reintentar();
      },
      error: (fallo: FalloHttp) => {
        this.eliminando.set(false);
        this.falloEliminacion.set(fallo.detalle ?? fallo.titulo);
      },
    });
  }

  protected reintentar(): void {
    this.intento.update((intento) => intento + 1);
  }

  private cargar<T>(id: number, consulta: (id: number) => Observable<T>): Observable<EstadoCarga<T>> {
    return Number.isInteger(id) && id > 0 ? consulta(id).pipe(conEstadoCarga()) : of(IDENTIFICADOR_NO_VALIDO);
  }
}

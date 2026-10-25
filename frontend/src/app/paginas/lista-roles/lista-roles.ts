import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, map, switchMap } from 'rxjs';

import { Aviso } from '../../compartido/aviso/aviso';
import { Paginacion } from '../../compartido/paginacion/paginacion';
import { TEXTO_VISIBILIDAD_ROL, explicarFallo } from '../../compartido/textos';
import { CARGANDO, EstadoCarga } from '../../core/modelos/estado-carga';
import { Pagina } from '../../core/modelos/pagina';
import {
  FiltroRolesProceso,
  RolProcesoResumen,
  VISIBILIDADES_ROL,
  VisibilidadRolProceso,
} from '../../core/modelos/rol-proceso';
import { conEstadoCarga } from '../../core/rxjs/con-estado-carga';
import { RolProcesoService } from '../../core/servicios/rol-proceso-service';
import { SesionService } from '../../core/servicios/sesion-service';

interface ValoresFiltro {
  q: string;
  visibilidad: VisibilidadRolProceso;
}

@Component({
  selector: 'app-lista-roles',
  imports: [ReactiveFormsModule, RouterLink, Aviso, Paginacion],
  templateUrl: './lista-roles.html',
  styleUrl: './lista-roles.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListaRoles {
  private readonly roles = inject(RolProcesoService);
  private readonly sesion = inject(SesionService);

  protected readonly puedeCrear = computed(() => this.sesion.tieneRol(['ADMINISTRADOR']));
  protected readonly visibilidades = VISIBILIDADES_ROL;
  protected readonly textoVisibilidad = TEXTO_VISIBILIDAD_ROL;

  protected readonly filtros = new FormGroup({
    q: new FormControl('', { nonNullable: true }),
    visibilidad: new FormControl<VisibilidadRolProceso>('ACTIVOS', { nonNullable: true }),
  });

  private readonly criterios = signal<FiltroRolesProceso>(criteriosDesde(this.filtros.getRawValue(), 0));

  protected readonly resultado = toSignal(
    toObservable(this.criterios).pipe(switchMap((criterios) => this.roles.listar(criterios).pipe(conEstadoCarga()))),
    { initialValue: CARGANDO as EstadoCarga<Pagina<RolProcesoResumen>> },
  );

  protected readonly cargando = computed(() => this.resultado().tipo === 'cargando');
  protected readonly pagina = computed(() => {
    const resultado = this.resultado();
    return resultado.tipo === 'listo' ? resultado.datos : null;
  });
  protected readonly fallo = computed(() => {
    const resultado = this.resultado();
    return resultado.tipo === 'error' ? resultado.fallo : null;
  });
  protected readonly explicar = explicarFallo;

  constructor() {
    this.filtros.valueChanges
      .pipe(
        debounceTime(300),
        map(() => criteriosDesde(this.filtros.getRawValue(), 0)),
        distinctUntilChanged((anterior, actual) => JSON.stringify(anterior) === JSON.stringify(actual)),
        takeUntilDestroyed(),
      )
      .subscribe((criterios) => this.criterios.set(criterios));
  }

  protected cambiarPagina(page: number): void {
    this.criterios.update((criterios) => ({ ...criterios, page }));
  }

  protected reintentar(): void {
    this.criterios.update((criterios) => ({ ...criterios }));
  }

  protected limpiarFiltros(): void {
    this.filtros.reset();
  }

  protected usos(rol: RolProcesoResumen): string {
    if (!rol.enUso) {
      return 'Sin uso';
    }
    const procesos = rol.procesos.length;
    return procesos === 1 ? '1 proceso' : `${procesos} procesos`;
  }
}

function criteriosDesde(valores: ValoresFiltro, page: number): FiltroRolesProceso {
  return { q: valores.q.trim() || null, visibilidad: valores.visibilidad, page };
}

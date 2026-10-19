import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, map, switchMap } from 'rxjs';

import { Aviso } from '../../compartido/aviso/aviso';
import { EtiquetaEstado } from '../../compartido/etiqueta-estado/etiqueta-estado';
import { Paginacion } from '../../compartido/paginacion/paginacion';
import { TEXTO_ALCANCE, TEXTO_ESTADO, TEXTO_VISIBILIDAD, explicarFallo } from '../../compartido/textos';
import { CARGANDO, EstadoCarga } from '../../core/modelos/estado-carga';
import { Pagina } from '../../core/modelos/pagina';
import {
  ALCANCES_PROCESO,
  AlcanceProceso,
  ESTADOS_PROCESO,
  EstadoProceso,
  FiltroProcesos,
  ProcesoResumen,
  VISIBILIDADES_PROCESO,
  VisibilidadProceso,
} from '../../core/modelos/proceso';
import { ROLES_ESCRITURA } from '../../core/modelos/sesion';
import { conEstadoCarga } from '../../core/rxjs/con-estado-carga';
import { ProcesoService } from '../../core/servicios/proceso-service';
import { SesionService } from '../../core/servicios/sesion-service';

interface ValoresFiltro {
  q: string;
  estado: EstadoProceso | '';
  categoria: string;
  alcance: AlcanceProceso;
  visibilidad: VisibilidadProceso;
}

@Component({
  selector: 'app-lista-procesos',
  imports: [ReactiveFormsModule, RouterLink, Aviso, EtiquetaEstado, Paginacion],
  templateUrl: './lista-procesos.html',
  styleUrl: './lista-procesos.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListaProcesos {
  private readonly procesos = inject(ProcesoService);
  private readonly sesion = inject(SesionService);

  protected readonly puedeCrear = computed(() => this.sesion.tieneRol(ROLES_ESCRITURA));

  protected readonly estados = ESTADOS_PROCESO;
  protected readonly alcances = ALCANCES_PROCESO;
  protected readonly visibilidades = VISIBILIDADES_PROCESO;
  protected readonly textoEstado = TEXTO_ESTADO;
  protected readonly textoAlcance = TEXTO_ALCANCE;
  protected readonly textoVisibilidad = TEXTO_VISIBILIDAD;

  protected readonly filtros = new FormGroup({
    q: new FormControl('', { nonNullable: true }),
    estado: new FormControl<EstadoProceso | ''>('', { nonNullable: true }),
    categoria: new FormControl('', { nonNullable: true }),
    alcance: new FormControl<AlcanceProceso>('PROPIOS', { nonNullable: true }),
    visibilidad: new FormControl<VisibilidadProceso>('ACTIVOS', { nonNullable: true }),
  });

  private readonly criterios = signal<FiltroProcesos>(criteriosDesde(this.filtros.getRawValue(), 0));

  protected readonly resultado = toSignal(
    toObservable(this.criterios).pipe(switchMap((criterios) => this.procesos.listar(criterios).pipe(conEstadoCarga()))),
    { initialValue: CARGANDO as EstadoCarga<Pagina<ProcesoResumen>> },
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
  protected readonly explicacionFallo = computed(() => {
    const fallo = this.fallo();
    return fallo ? explicarFallo(fallo) : null;
  });

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
}

function criteriosDesde(valores: ValoresFiltro, page: number): FiltroProcesos {
  return {
    q: valores.q.trim() || null,
    estado: valores.estado || null,
    categoria: valores.categoria.trim() || null,
    alcance: valores.alcance,
    visibilidad: valores.visibilidad,
    page,
  };
}

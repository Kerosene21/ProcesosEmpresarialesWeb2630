import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { switchMap } from 'rxjs';

import { Aviso } from '../../compartido/aviso/aviso';
import { TEXTO_ROL, explicarFallo } from '../../compartido/textos';
import { Empresa } from '../../core/modelos/empresa';
import { CARGANDO, EstadoCarga } from '../../core/modelos/estado-carga';
import { conEstadoCarga } from '../../core/rxjs/con-estado-carga';
import { EmpresaService } from '../../core/servicios/empresa-service';
import { SesionService } from '../../core/servicios/sesion-service';

@Component({
  selector: 'app-mi-empresa',
  imports: [Aviso],
  templateUrl: './mi-empresa.html',
  styleUrl: './mi-empresa.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MiEmpresa {
  private readonly empresas = inject(EmpresaService);

  protected readonly sesion = inject(SesionService).sesion;
  protected readonly textoRol = TEXTO_ROL;
  protected readonly explicar = explicarFallo;
  private readonly intento = signal(0);

  protected readonly estado = toSignal(
    toObservable(this.intento).pipe(switchMap(() => this.empresas.actual().pipe(conEstadoCarga()))),
    { initialValue: CARGANDO as EstadoCarga<Empresa> },
  );
  protected readonly empresa = computed(() => {
    const estado = this.estado();
    return estado.tipo === 'listo' ? estado.datos : null;
  });
  protected readonly fallo = computed(() => {
    const estado = this.estado();
    return estado.tipo === 'error' ? estado.fallo : null;
  });

  protected reintentar(): void {
    this.intento.update((intento) => intento + 1);
  }
}

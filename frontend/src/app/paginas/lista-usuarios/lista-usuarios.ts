import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { switchMap } from 'rxjs';

import { Aviso } from '../../compartido/aviso/aviso';
import { TEXTO_ROL, explicarFallo } from '../../compartido/textos';
import { CARGANDO, EstadoCarga } from '../../core/modelos/estado-carga';
import { Usuario } from '../../core/modelos/usuario';
import { conEstadoCarga } from '../../core/rxjs/con-estado-carga';
import { SesionService } from '../../core/servicios/sesion-service';
import { UsuarioService } from '../../core/servicios/usuario-service';

@Component({
  selector: 'app-lista-usuarios',
  imports: [RouterLink, Aviso],
  templateUrl: './lista-usuarios.html',
  styleUrl: './lista-usuarios.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ListaUsuarios {
  private readonly usuarios = inject(UsuarioService);
  private readonly sesion = inject(SesionService);

  protected readonly esAdministrador = computed(() => this.sesion.tieneRol(['ADMINISTRADOR']));
  protected readonly textoRol = TEXTO_ROL;
  protected readonly explicar = explicarFallo;
  private readonly intento = signal(0);

  protected readonly resultado = toSignal(
    toObservable(this.intento).pipe(switchMap(() => this.usuarios.listar().pipe(conEstadoCarga()))),
    { initialValue: CARGANDO as EstadoCarga<Usuario[]> },
  );
  protected readonly cargando = computed(() => this.resultado().tipo === 'cargando');
  protected readonly lista = computed(() => {
    const resultado = this.resultado();
    return resultado.tipo === 'listo' ? resultado.datos : null;
  });
  protected readonly fallo = computed(() => {
    const resultado = this.resultado();
    return resultado.tipo === 'error' ? resultado.fallo : null;
  });
  protected readonly activos = computed(() => this.lista()?.filter((usuario) => usuario.activo).length ?? 0);

  protected esPropio(usuario: Usuario): boolean {
    return usuario.correo === this.sesion.sesion()?.correo;
  }

  protected reintentar(): void {
    this.intento.update((intento) => intento + 1);
  }
}

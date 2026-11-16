import { ChangeDetectionStrategy, Component, computed, inject, input, linkedSignal, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable, of, switchMap, tap } from 'rxjs';

import { Aviso } from '../../compartido/aviso/aviso';
import { DialogoConfirmacion } from '../../compartido/dialogo-confirmacion/dialogo-confirmacion';
import { TEXTO_ROL, explicarFallo } from '../../compartido/textos';
import { CARGANDO, EstadoCarga } from '../../core/modelos/estado-carga';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { RolUsuario } from '../../core/modelos/sesion';
import { ROLES_USUARIO, Usuario } from '../../core/modelos/usuario';
import { conEstadoCarga } from '../../core/rxjs/con-estado-carga';
import { SesionService } from '../../core/servicios/sesion-service';
import { UsuarioService } from '../../core/servicios/usuario-service';

const IDENTIFICADOR_NO_VALIDO: EstadoCarga<never> = {
  tipo: 'error',
  fallo: {
    tipo: 'no-encontrado',
    estado: 0,
    codigo: null,
    titulo: 'El identificador del usuario no es válido',
    detalle: null,
  },
};

@Component({
  selector: 'app-detalle-usuario',
  imports: [ReactiveFormsModule, RouterLink, Aviso, DialogoConfirmacion],
  templateUrl: './detalle-usuario.html',
  styleUrl: './detalle-usuario.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DetalleUsuario {
  private readonly usuarios = inject(UsuarioService);
  private readonly sesion = inject(SesionService);

  readonly id = input.required<string>();

  protected readonly roles = ROLES_USUARIO;
  protected readonly textoRol = TEXTO_ROL;
  protected readonly explicar = explicarFallo;
  private readonly intento = signal(0);

  protected readonly estado = toSignal(
    toObservable(computed(() => ({ id: this.id(), intento: this.intento() }))).pipe(
      switchMap(({ id }) => this.cargar(id)),
    ),
    { initialValue: CARGANDO as EstadoCarga<Usuario> },
  );
  protected readonly usuario = linkedSignal(() => {
    const estado = this.estado();
    return estado.tipo === 'listo' ? estado.datos : null;
  });
  protected readonly fallo = computed(() => {
    const estado = this.estado();
    return estado.tipo === 'error' ? estado.fallo : null;
  });
  protected readonly esAdministrador = computed(() => this.sesion.tieneRol(['ADMINISTRADOR']));
  protected readonly propio = computed(() => this.usuario()?.correo === this.sesion.sesion()?.correo);
  protected readonly gestionable = computed(() => this.esAdministrador() && this.usuario() !== null && !this.propio());

  protected readonly formularioRol = new FormGroup({
    rol: new FormControl<RolUsuario | null>(null, { validators: [Validators.required] }),
  });
  protected readonly rol = this.formularioRol.controls.rol;
  protected readonly guardando = signal(false);
  protected readonly mensaje = signal<string | null>(null);
  protected readonly falloAccion = signal<FalloHttp | null>(null);
  protected readonly confirmando = signal(false);
  protected readonly desactivando = signal(false);
  protected readonly falloDesactivacion = signal<string | null>(null);

  protected reintentar(): void {
    this.intento.update((intento) => intento + 1);
  }

  protected guardarRol(): void {
    const usuario = this.usuario();
    const rol = this.rol.value;
    if (!usuario || this.guardando()) {
      return;
    }
    if (rol === null) {
      this.rol.markAsTouched();
      return;
    }
    this.mensaje.set(null);
    this.falloAccion.set(null);
    this.guardando.set(true);
    this.usuarios.cambiarRol(usuario.id, { rol }).subscribe({
      next: (actualizado) => {
        this.guardando.set(false);
        this.aplicar(actualizado);
        this.mensaje.set(`El rol de ${actualizado.correo} ahora es ${TEXTO_ROL[actualizado.rol]}.`);
      },
      error: (fallo: FalloHttp) => {
        this.guardando.set(false);
        this.falloAccion.set(fallo);
      },
    });
  }

  protected solicitarDesactivacion(): void {
    this.mensaje.set(null);
    this.falloAccion.set(null);
    this.falloDesactivacion.set(null);
    this.confirmando.set(true);
  }

  protected cancelarDesactivacion(): void {
    this.confirmando.set(false);
    this.falloDesactivacion.set(null);
  }

  protected desactivar(): void {
    const usuario = this.usuario();
    if (!usuario || this.desactivando()) {
      return;
    }
    this.desactivando.set(true);
    this.falloDesactivacion.set(null);
    this.usuarios.desactivar(usuario.id).subscribe({
      next: (desactivado) => {
        this.desactivando.set(false);
        this.confirmando.set(false);
        this.aplicar(desactivado);
        this.mensaje.set(`${desactivado.correo} quedó inactivo y conserva su historial.`);
      },
      error: (fallo: FalloHttp) => {
        this.desactivando.set(false);
        this.falloDesactivacion.set(fallo.detalle ?? fallo.titulo);
      },
    });
  }

  private aplicar(usuario: Usuario): void {
    this.usuario.set(usuario);
    this.rol.setValue(usuario.rol);
  }

  private cargar(id: string): Observable<EstadoCarga<Usuario>> {
    const numero = Number(id);
    if (!Number.isInteger(numero) || numero <= 0) {
      return of(IDENTIFICADOR_NO_VALIDO);
    }
    return this.usuarios.obtener(numero).pipe(
      tap((usuario) => this.rol.setValue(usuario.rol)),
      conEstadoCarga(),
    );
  }
}

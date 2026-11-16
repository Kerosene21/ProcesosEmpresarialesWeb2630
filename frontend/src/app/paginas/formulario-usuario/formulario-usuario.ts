import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { Aviso } from '../../compartido/aviso/aviso';
import { TEXTO_ROL, explicarFallo } from '../../compartido/textos';
import { aplicarErroresDelServidor, errorDelServidor } from '../../core/formularios/errores-servidor';
import { noEnBlanco } from '../../core/formularios/validadores';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { RolUsuario } from '../../core/modelos/sesion';
import { LIMITES_USUARIO, ROLES_USUARIO } from '../../core/modelos/usuario';
import { UsuarioService } from '../../core/servicios/usuario-service';

type Campo = 'correo' | 'password' | 'rol';

const CAMPOS_POR_CODIGO: Readonly<Record<string, Campo>> = {
  USUARIO_CORREO_EN_USO: 'correo',
};

@Component({
  selector: 'app-formulario-usuario',
  imports: [ReactiveFormsModule, RouterLink, Aviso],
  templateUrl: './formulario-usuario.html',
  styleUrl: './formulario-usuario.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FormularioUsuario {
  private readonly usuarios = inject(UsuarioService);
  private readonly router = inject(Router);

  protected readonly roles = ROLES_USUARIO;
  protected readonly textoRol = TEXTO_ROL;
  protected readonly limites = LIMITES_USUARIO;
  protected readonly explicar = explicarFallo;

  protected readonly formulario = new FormGroup({
    correo: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, noEnBlanco, Validators.email, Validators.maxLength(LIMITES_USUARIO.correo)],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        noEnBlanco,
        Validators.minLength(LIMITES_USUARIO.passwordMinima),
        Validators.maxLength(LIMITES_USUARIO.passwordMaxima),
      ],
    }),
    rol: new FormControl<RolUsuario | null>(null, { validators: [Validators.required] }),
  });

  protected readonly guardando = signal(false);
  protected readonly conErrores = signal(false);
  protected readonly falloGuardado = signal<FalloHttp | null>(null);

  constructor() {
    this.formulario.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.conErrores.set(false));
  }

  protected error(campo: Campo): string | null {
    const control = this.formulario.controls[campo];
    if (control.valid || !control.touched) {
      return null;
    }
    const servidor = errorDelServidor(control);
    if (servidor) {
      return servidor;
    }
    switch (campo) {
      case 'correo':
        if (control.hasError('email')) {
          return 'El correo no tiene un formato válido';
        }
        return control.hasError('maxlength')
          ? `El correo no puede superar ${LIMITES_USUARIO.correo} caracteres`
          : 'El correo del usuario es obligatorio';
      case 'password':
        return control.hasError('minlength') || control.hasError('maxlength')
          ? `La contraseña debe tener entre ${LIMITES_USUARIO.passwordMinima} y ${LIMITES_USUARIO.passwordMaxima} caracteres`
          : 'La contraseña inicial es obligatoria';
      case 'rol':
        return 'El rol de acceso es obligatorio';
    }
  }

  protected guardar(): void {
    if (this.guardando()) {
      return;
    }
    this.falloGuardado.set(null);
    const { correo, password, rol } = this.formulario.getRawValue();
    if (this.formulario.invalid || rol === null) {
      this.formulario.markAllAsTouched();
      this.conErrores.set(true);
      return;
    }
    this.guardando.set(true);
    this.usuarios.crear({ correo: correo.trim(), password, rol }).subscribe({
      next: (creado) => void this.router.navigate(['/usuarios', creado.id]),
      error: (fallo: FalloHttp) => {
        this.guardando.set(false);
        if (aplicarErroresDelServidor(this.formulario, fallo, CAMPOS_POR_CODIGO)) {
          this.conErrores.set(true);
        } else {
          this.falloGuardado.set(fallo);
        }
      },
    });
  }
}

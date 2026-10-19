import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { Aviso } from '../../compartido/aviso/aviso';
import { explicarFallo } from '../../compartido/textos';
import { aplicarErroresDelServidor, errorDelServidor } from '../../core/formularios/errores-servidor';
import { noEnBlanco } from '../../core/formularios/validadores';
import { LIMITES_EMPRESA } from '../../core/modelos/empresa';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { RUTA_INICIO_SESION } from '../../core/seguridad/sesion-guard';
import { EmpresaService } from '../../core/servicios/empresa-service';

type Campo = 'nombre' | 'nit' | 'correoContacto' | 'passwordAdministrador';

const MENSAJES: Record<Campo, { obligatorio: string; longitud: string; formato?: string }> = {
  nombre: {
    obligatorio: 'El nombre de la empresa es obligatorio',
    longitud: `El nombre no puede superar ${LIMITES_EMPRESA.nombre} caracteres`,
  },
  nit: {
    obligatorio: 'El NIT es obligatorio',
    longitud: `El NIT no puede superar ${LIMITES_EMPRESA.nit} caracteres`,
  },
  correoContacto: {
    obligatorio: 'El correo de contacto es obligatorio',
    longitud: `El correo no puede superar ${LIMITES_EMPRESA.correoContacto} caracteres`,
    formato: 'El correo de contacto no tiene un formato válido',
  },
  passwordAdministrador: {
    obligatorio: 'La contraseña del administrador inicial es obligatoria',
    longitud: `La contraseña debe tener entre ${LIMITES_EMPRESA.passwordMinimo} y ${LIMITES_EMPRESA.passwordMaximo} caracteres`,
  },
};

const CAMPOS_POR_CONFLICTO: Readonly<Record<string, Campo>> = {
  EMPRESA_NIT_DUPLICADO: 'nit',
  USUARIO_CORREO_EN_USO: 'correoContacto',
};

@Component({
  selector: 'app-registro-empresa',
  imports: [ReactiveFormsModule, RouterLink, Aviso],
  templateUrl: './registro-empresa.html',
  styleUrl: './registro-empresa.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegistroEmpresa {
  private readonly empresas = inject(EmpresaService);
  private readonly router = inject(Router);

  protected readonly limites = LIMITES_EMPRESA;
  protected readonly formulario = new FormGroup({
    nombre: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, noEnBlanco, Validators.maxLength(LIMITES_EMPRESA.nombre)],
    }),
    nit: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, noEnBlanco, Validators.maxLength(LIMITES_EMPRESA.nit)],
    }),
    correoContacto: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(LIMITES_EMPRESA.correoContacto)],
    }),
    passwordAdministrador: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        noEnBlanco,
        Validators.minLength(LIMITES_EMPRESA.passwordMinimo),
        Validators.maxLength(LIMITES_EMPRESA.passwordMaximo),
      ],
    }),
  });

  protected readonly enviando = signal(false);
  protected readonly fallo = signal<FalloHttp | null>(null);
  protected readonly conErrores = signal(false);
  protected readonly explicar = explicarFallo;

  protected registrar(): void {
    this.fallo.set(null);
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      this.conErrores.set(true);
      return;
    }
    this.conErrores.set(false);
    this.enviando.set(true);
    this.empresas.registrar(this.formulario.getRawValue()).subscribe({
      next: () => void this.router.navigate([RUTA_INICIO_SESION], { queryParams: { motivo: 'registrada' } }),
      error: (fallo: FalloHttp) => {
        this.enviando.set(false);
        if (aplicarErroresDelServidor(this.formulario, fallo, CAMPOS_POR_CONFLICTO)) {
          this.conErrores.set(true);
        } else {
          this.fallo.set(fallo);
        }
      },
    });
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
    if (control.hasError('required') || control.hasError('enBlanco')) {
      return MENSAJES[campo].obligatorio;
    }
    if (control.hasError('email')) {
      return MENSAJES[campo].formato ?? MENSAJES[campo].obligatorio;
    }
    return MENSAJES[campo].longitud;
  }
}

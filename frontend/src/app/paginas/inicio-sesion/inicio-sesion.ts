import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { Aviso } from '../../compartido/aviso/aviso';
import { explicarFallo } from '../../compartido/textos';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { RUTA_PRINCIPAL } from '../../core/seguridad/invitado-guard';
import { RUTA_INICIO_SESION } from '../../core/seguridad/sesion-guard';
import { SesionService } from '../../core/servicios/sesion-service';

const LIMITES = { correo: 180, password: 100 } as const;

const AVISOS: Readonly<Record<string, string>> = {
  expirada: 'Tu sesión expiró. Inicia sesión de nuevo para continuar.',
  registrada: 'La empresa quedó registrada. Inicia sesión con el correo de contacto y la contraseña del administrador.',
  cerrada: 'Cerraste la sesión.',
};

@Component({
  selector: 'app-inicio-sesion',
  imports: [ReactiveFormsModule, RouterLink, Aviso],
  templateUrl: './inicio-sesion.html',
  styleUrl: './inicio-sesion.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InicioSesion {
  private readonly sesion = inject(SesionService);
  private readonly router = inject(Router);

  readonly retorno = input<string>();
  readonly motivo = input<string>();

  protected readonly limites = LIMITES;
  protected readonly formulario = new FormGroup({
    correo: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(LIMITES.correo)],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(LIMITES.password)],
    }),
  });

  protected readonly enviando = signal(false);
  protected readonly fallo = signal<FalloHttp | null>(null);
  protected readonly aviso = computed(() => AVISOS[this.motivo() ?? ''] ?? null);
  protected readonly mensajeFallo = computed(() => {
    const fallo = this.fallo();
    if (!fallo) {
      return null;
    }
    if (fallo.codigo === 'CREDENCIALES_INVALIDAS') {
      return { titulo: 'No se pudo iniciar sesión', detalle: fallo.detalle };
    }
    if (fallo.tipo === 'sin-permiso') {
      return { titulo: 'La solicitud no se pudo validar', detalle: 'Vuelve a intentarlo.' };
    }
    return { titulo: fallo.titulo, detalle: explicarFallo(fallo) };
  });

  protected iniciar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched();
      return;
    }
    this.enviando.set(true);
    this.fallo.set(null);
    this.sesion.iniciar(this.formulario.getRawValue()).subscribe({
      next: () => void this.router.navigateByUrl(destinoSeguro(this.retorno())),
      error: (fallo: FalloHttp) => {
        this.enviando.set(false);
        this.fallo.set(fallo);
        this.formulario.controls.password.reset();
        if (fallo.tipo === 'sin-permiso') {
          this.sesion.cargar().subscribe({ error: () => undefined });
        }
      },
    });
  }

  protected error(campo: 'correo' | 'password'): string | null {
    const control = this.formulario.controls[campo];
    if (control.valid || !control.touched) {
      return null;
    }
    if (control.hasError('required')) {
      return campo === 'correo' ? 'El correo es obligatorio' : 'La contraseña es obligatoria';
    }
    if (control.hasError('email')) {
      return 'El correo no tiene un formato válido';
    }
    return `No puede superar ${LIMITES[campo]} caracteres`;
  }
}

export function destinoSeguro(retorno: string | undefined): string {
  const interno =
    retorno !== undefined &&
    retorno.startsWith('/') &&
    !retorno.startsWith('//') &&
    !retorno.startsWith('/\\') &&
    !retorno.startsWith(RUTA_INICIO_SESION);
  return interno ? retorno : RUTA_PRINCIPAL;
}

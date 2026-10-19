import { AbstractControl } from '@angular/forms';

import { FalloHttp } from '../modelos/fallo-http';

export function aplicarErroresDelServidor(
  formulario: AbstractControl,
  fallo: FalloHttp,
  camposPorCodigo: Readonly<Record<string, string>> = {},
): boolean {
  let aplicado = false;
  const asignar = (campo: string, mensaje: string): void => {
    const control = formulario.get(campo);
    if (control) {
      control.setErrors({ ...control.errors, servidor: mensaje });
      control.markAsTouched();
      aplicado = true;
    }
  };
  Object.entries(fallo.campos ?? {}).forEach(([campo, mensaje]) => asignar(campo, mensaje));
  const campoDelCodigo = fallo.codigo ? camposPorCodigo[fallo.codigo] : undefined;
  if (campoDelCodigo && fallo.detalle) {
    asignar(campoDelCodigo, fallo.detalle);
  }
  return aplicado;
}

export function errorDelServidor(control: AbstractControl): string | null {
  const mensaje: unknown = control.errors?.['servidor'];
  return typeof mensaje === 'string' ? mensaje : null;
}

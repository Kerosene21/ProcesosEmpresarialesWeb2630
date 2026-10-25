import { AbstractControl, ValidationErrors } from '@angular/forms';

export function noEnBlanco(control: AbstractControl): ValidationErrors | null {
  const valor = control.value;
  return typeof valor === 'string' && valor.length > 0 && valor.trim().length === 0 ? { enBlanco: true } : null;
}

export function entero(control: AbstractControl): ValidationErrors | null {
  const valor: unknown = control.value;
  return valor === null || valor === '' || Number.isInteger(valor) ? null : { entero: true };
}

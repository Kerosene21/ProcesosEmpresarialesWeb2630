import { FormControl, FormGroup, Validators } from '@angular/forms';

import { FalloHttp } from '../modelos/fallo-http';
import { aplicarErroresDelServidor, errorDelServidor } from './errores-servidor';

function formulario(): FormGroup {
  return new FormGroup({
    nombre: new FormControl('Ventas', { nonNullable: true, validators: [Validators.required] }),
    nit: new FormControl('900', { nonNullable: true }),
  });
}

function fallo(parcial: Partial<FalloHttp>): FalloHttp {
  return { tipo: 'datos-no-validos', estado: 400, codigo: null, titulo: 'x', detalle: null, ...parcial };
}

describe('aplicarErroresDelServidor', () => {
  it('asigna a cada control el mensaje de validación del backend y lo marca como tocado', () => {
    const grupo = formulario();

    const aplicado = aplicarErroresDelServidor(grupo, fallo({ campos: { nombre: 'El nombre es obligatorio' } }));

    expect(aplicado).toBe(true);
    expect(errorDelServidor(grupo.controls['nombre'])).toBe('El nombre es obligatorio');
    expect(grupo.controls['nombre'].touched).toBe(true);
    expect(grupo.controls['nit'].valid).toBe(true);
  });

  it('asocia el conflicto de negocio al campo indicado por su código', () => {
    const grupo = formulario();

    aplicarErroresDelServidor(grupo, fallo({ estado: 409, codigo: 'EMPRESA_NIT_DUPLICADO', detalle: 'NIT repetido' }), {
      EMPRESA_NIT_DUPLICADO: 'nit',
    });

    expect(errorDelServidor(grupo.controls['nit'])).toBe('NIT repetido');
  });

  it('no aplica nada cuando el fallo no corresponde a ningún campo', () => {
    const grupo = formulario();

    expect(aplicarErroresDelServidor(grupo, fallo({ campos: { inexistente: 'x' } }))).toBe(false);
    expect(aplicarErroresDelServidor(grupo, fallo({ estado: 500, tipo: 'servidor' }))).toBe(false);
    expect(grupo.valid).toBe(true);
  });

  it('el error del servidor desaparece cuando el usuario corrige el valor', () => {
    const grupo = formulario();
    aplicarErroresDelServidor(grupo, fallo({ campos: { nombre: 'Ya existe' } }));

    grupo.controls['nombre'].setValue('Compras');

    expect(errorDelServidor(grupo.controls['nombre'])).toBeNull();
    expect(grupo.valid).toBe(true);
  });
});

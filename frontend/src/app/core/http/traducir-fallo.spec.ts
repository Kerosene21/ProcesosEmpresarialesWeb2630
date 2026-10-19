import { HttpErrorResponse } from '@angular/common/http';

import { lanzarFallo, traducirFallo } from './traducir-fallo';

describe('traducirFallo', () => {
  it('reconoce la falta de conexión con el servidor', () => {
    const fallo = traducirFallo(new HttpErrorResponse({ status: 0 }));

    expect(fallo.tipo).toBe('conexion');
    expect(fallo.codigo).toBeNull();
  });

  it('conserva el código y el mensaje del contrato ErrorApi', () => {
    const fallo = traducirFallo(
      new HttpErrorResponse({
        status: 404,
        error: { codigo: 'RECURSO_NO_ENCONTRADO', mensaje: 'El proceso no existe' },
      }),
    );

    expect(fallo).toEqual({
      tipo: 'no-encontrado',
      estado: 404,
      codigo: 'RECURSO_NO_ENCONTRADO',
      titulo: 'El recurso solicitado no existe',
      detalle: 'El proceso no existe',
    });
  });

  it('no inventa un código cuando el cuerpo no sigue el contrato ErrorApi', () => {
    const fallo = traducirFallo(
      new HttpErrorResponse({ status: 400, error: { timestamp: 'x', status: 400, error: 'Bad Request', path: '/api' } }),
    );

    expect(fallo.tipo).toBe('datos-no-validos');
    expect(fallo.codigo).toBeNull();
    expect(fallo.detalle).toBeNull();
  });

  it('conserva los mensajes por campo de la validación del backend e ignora valores que no son texto', () => {
    const fallo = traducirFallo(
      new HttpErrorResponse({
        status: 400,
        error: {
          codigo: 'DATOS_NO_VALIDOS',
          mensaje: 'Hay campos con valores no válidos',
          campos: { nombre: 'El nombre es obligatorio', categoria: 3 },
        },
      }),
    );

    expect(fallo.codigo).toBe('DATOS_NO_VALIDOS');
    expect(fallo.campos).toEqual({ nombre: 'El nombre es obligatorio' });
  });

  it('lanza el fallo ya traducido para los observables de los servicios', () => {
    let recibido: unknown;

    lanzarFallo(new HttpErrorResponse({ status: 409, error: { codigo: 'X', mensaje: 'y' } })).subscribe({
      error: (error) => (recibido = error),
    });

    expect(recibido).toMatchObject({ tipo: 'conflicto', estado: 409, codigo: 'X' });
  });

  it('agrupa los errores 5xx como fallos del servidor', () => {
    expect(traducirFallo(new HttpErrorResponse({ status: 503 })).tipo).toBe('servidor');
  });

  it('trata errores ajenos a HTTP como desconocidos y no traduce dos veces', () => {
    const desconocido = traducirFallo(new Error('fallo'));

    expect(desconocido.tipo).toBe('desconocido');
    expect(traducirFallo(desconocido)).toBe(desconocido);
  });
});

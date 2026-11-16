import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';

import { EstadoCarga } from '../modelos/estado-carga';
import { conEstadoCarga } from './con-estado-carga';

describe('conEstadoCarga', () => {
  it('emite cargando y después los datos', () => {
    const estados: EstadoCarga<number>[] = [];

    of(5).pipe(conEstadoCarga()).subscribe((estado) => estados.push(estado));

    expect(estados).toEqual([{ tipo: 'cargando' }, { tipo: 'listo', datos: 5 }]);
  });

  it('emite cargando y después el fallo sin propagar el error', () => {
    const estados: EstadoCarga<number>[] = [];

    throwError(() => new HttpErrorResponse({ status: 0 }))
      .pipe(conEstadoCarga<number>())
      .subscribe({ next: (estado) => estados.push(estado), error: () => estados.push({ tipo: 'cargando' }) });

    expect(estados.map((estado) => estado.tipo)).toEqual(['cargando', 'error']);
  });
});

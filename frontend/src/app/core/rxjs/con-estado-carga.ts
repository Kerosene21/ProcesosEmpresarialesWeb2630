import { Observable, OperatorFunction, catchError, map, of, startWith } from 'rxjs';

import { traducirFallo } from '../http/traducir-fallo';
import { CARGANDO, EstadoCarga } from '../modelos/estado-carga';

export function conEstadoCarga<T>(): OperatorFunction<T, EstadoCarga<T>> {
  return (fuente: Observable<T>) =>
    fuente.pipe(
      map((datos): EstadoCarga<T> => ({ tipo: 'listo', datos })),
      catchError((error: unknown) => of<EstadoCarga<T>>({ tipo: 'error', fallo: traducirFallo(error) })),
      startWith<EstadoCarga<T>>(CARGANDO),
    );
}

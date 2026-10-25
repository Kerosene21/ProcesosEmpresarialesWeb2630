import { HttpParams } from '@angular/common/http';

export function parametrosDe(filtro: object): HttpParams {
  return Object.entries(filtro).reduce((parametros, [clave, valor]) => {
    const texto = valor === null || valor === undefined ? '' : String(valor).trim();
    return texto ? parametros.set(clave, texto) : parametros;
  }, new HttpParams());
}

import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError } from 'rxjs';

import { lanzarFallo } from '../http/traducir-fallo';
import { Empresa, RegistroEmpresa } from '../modelos/empresa';

@Injectable({
  providedIn: 'root',
})
export class EmpresaService {
  private readonly http = inject(HttpClient);
  private readonly ruta = '/api/empresas';

  registrar(datos: RegistroEmpresa): Observable<Empresa> {
    return this.http.post<Empresa>(this.ruta, datos).pipe(catchError(lanzarFallo));
  }

  actual(): Observable<Empresa> {
    return this.http.get<Empresa>(`${this.ruta}/actual`).pipe(catchError(lanzarFallo));
  }
}

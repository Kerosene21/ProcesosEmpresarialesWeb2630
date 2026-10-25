import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError } from 'rxjs';

import { lanzarFallo } from '../http/traducir-fallo';
import { CambiarRolUsuario, CrearUsuario, Usuario } from '../modelos/usuario';

@Injectable({
  providedIn: 'root',
})
export class UsuarioService {
  private readonly http = inject(HttpClient);
  private readonly ruta = '/api/usuarios';

  listar(): Observable<Usuario[]> {
    return this.http.get<Usuario[]>(this.ruta).pipe(catchError(lanzarFallo));
  }

  obtener(id: number): Observable<Usuario> {
    return this.http.get<Usuario>(`${this.ruta}/${id}`).pipe(catchError(lanzarFallo));
  }

  crear(datos: CrearUsuario): Observable<Usuario> {
    return this.http.post<Usuario>(this.ruta, datos).pipe(catchError(lanzarFallo));
  }

  cambiarRol(id: number, datos: CambiarRolUsuario): Observable<Usuario> {
    return this.http.put<Usuario>(`${this.ruta}/${id}/rol`, datos).pipe(catchError(lanzarFallo));
  }

  desactivar(id: number): Observable<Usuario> {
    return this.http.post<Usuario>(`${this.ruta}/${id}/desactivacion`, null).pipe(catchError(lanzarFallo));
  }
}

import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { Observable, firstValueFrom, of, throwError } from 'rxjs';

import { ROLES_ESCRITURA, RolUsuario, SESION_ANONIMA, Sesion } from '../modelos/sesion';
import { SesionService } from '../servicios/sesion-service';
import { rolGuard } from './rol-guard';

describe('rolGuard', () => {
  const executeGuard: CanActivateFn = (...guardParameters) =>
    TestBed.runInInjectionContext(() => rolGuard(...guardParameters));

  async function evaluar(rol: RolUsuario | null, asegurar?: Observable<Sesion>): Promise<boolean | string> {
    const sesion: Sesion = { ...SESION_ANONIMA, autenticado: rol !== null, rol };
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: SesionService,
          useValue: {
            asegurar: () => asegurar ?? of(sesion),
            tieneRol: (roles: readonly RolUsuario[]) => rol !== null && roles.includes(rol),
          },
        },
      ],
    });
    const ruta = { data: { roles: ROLES_ESCRITURA } } as unknown as ActivatedRouteSnapshot;
    const resultado = await firstValueFrom(
      executeGuard(ruta, { url: '/procesos/nuevo' } as RouterStateSnapshot) as Observable<boolean | UrlTree>,
    );
    return resultado instanceof UrlTree ? TestBed.inject(Router).serializeUrl(resultado) : resultado;
  }

  it('permite crear procesos al administrador y al editor', async () => {
    expect(await evaluar('ADMINISTRADOR')).toBe(true);
    TestBed.resetTestingModule();
    expect(await evaluar('EDITOR')).toBe(true);
  });

  it('devuelve al listado a un usuario de solo lectura', async () => {
    expect(await evaluar('SOLO_LECTURA')).toBe('/procesos');
  });

  it('lleva al inicio de sesión si la sesión no se puede consultar', async () => {
    expect(await evaluar(null, throwError(() => new Error('sin conexión')))).toBe('/iniciar-sesion');
  });
});

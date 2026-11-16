import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { Observable, firstValueFrom, of, throwError } from 'rxjs';

import { SESION_ANONIMA, Sesion } from '../modelos/sesion';
import { SesionService } from '../servicios/sesion-service';
import { sesionGuard } from './sesion-guard';

describe('sesionGuard', () => {
  const executeGuard: CanActivateFn = (...guardParameters) =>
    TestBed.runInInjectionContext(() => sesionGuard(...guardParameters));

  function configurar(asegurar: Observable<Sesion>): void {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: SesionService, useValue: { asegurar: () => asegurar } }],
    });
  }

  async function evaluar(url: string): Promise<boolean | string> {
    const resultado = await firstValueFrom(
      executeGuard({} as ActivatedRouteSnapshot, { url } as RouterStateSnapshot) as Observable<boolean | UrlTree>,
    );
    return resultado instanceof UrlTree ? TestBed.inject(Router).serializeUrl(resultado) : resultado;
  }

  it('deja pasar a un usuario autenticado', async () => {
    configurar(of({ ...SESION_ANONIMA, autenticado: true, correo: 'admin@alpes.com', rol: 'ADMINISTRADOR' }));

    expect(await evaluar('/procesos')).toBe(true);
  });

  it('lleva al inicio de sesión recordando la ruta solicitada', async () => {
    configurar(of(SESION_ANONIMA));

    expect(await evaluar('/procesos/5')).toBe('/iniciar-sesion?retorno=%2Fprocesos%2F5');
  });

  it('lleva al inicio de sesión si no se puede consultar la sesión', async () => {
    configurar(throwError(() => new Error('sin conexión')));

    expect(await evaluar('/mi-empresa')).toBe('/iniciar-sesion?retorno=%2Fmi-empresa');
  });
});

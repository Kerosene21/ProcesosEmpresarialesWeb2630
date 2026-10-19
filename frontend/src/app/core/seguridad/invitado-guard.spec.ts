import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { Observable, firstValueFrom, of, throwError } from 'rxjs';

import { SESION_ANONIMA, Sesion } from '../modelos/sesion';
import { SesionService } from '../servicios/sesion-service';
import { invitadoGuard } from './invitado-guard';

describe('invitadoGuard', () => {
  const executeGuard: CanActivateFn = (...guardParameters) =>
    TestBed.runInInjectionContext(() => invitadoGuard(...guardParameters));

  async function evaluar(asegurar: Observable<Sesion>): Promise<boolean | string> {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: SesionService, useValue: { asegurar: () => asegurar } }],
    });
    const resultado = await firstValueFrom(
      executeGuard({} as ActivatedRouteSnapshot, { url: '/iniciar-sesion' } as RouterStateSnapshot) as Observable<
        boolean | UrlTree
      >,
    );
    return resultado instanceof UrlTree ? TestBed.inject(Router).serializeUrl(resultado) : resultado;
  }

  it('muestra el inicio de sesión a un visitante anónimo', async () => {
    expect(await evaluar(of(SESION_ANONIMA))).toBe(true);
  });

  it('envía al listado de procesos a quien ya inició sesión', async () => {
    expect(await evaluar(of({ ...SESION_ANONIMA, autenticado: true, rol: 'EDITOR' }))).toBe('/procesos');
  });

  it('deja ver el inicio de sesión aunque la consulta de la sesión falle', async () => {
    expect(await evaluar(throwError(() => new Error('sin conexión')))).toBe(true);
  });
});

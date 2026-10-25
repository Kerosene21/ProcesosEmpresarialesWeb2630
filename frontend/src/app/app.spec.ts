import { computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { App } from './app';
import { routes } from './app.routes';
import { RolUsuario, SESION_ANONIMA, Sesion } from './core/modelos/sesion';
import { rolGuard } from './core/seguridad/rol-guard';
import { sesionGuard } from './core/seguridad/sesion-guard';
import { SesionService } from './core/servicios/sesion-service';

describe('App', () => {
  const estado = signal<Sesion | null>(null);
  const cerrar = vi.fn<() => Observable<void>>();
  let fixture: ComponentFixture<App>;

  async function crear(sesion: Sesion): Promise<HTMLElement> {
    estado.set(sesion);
    cerrar.mockReset();
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        {
          provide: SesionService,
          useValue: {
            sesion: estado.asReadonly(),
            autenticado: computed(() => estado()?.autenticado === true),
            tieneRol: (roles: readonly RolUsuario[]) => {
              const rol = estado()?.rol;
              return rol !== null && rol !== undefined && roles.includes(rol);
            },
            cerrar,
          },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  function enlaces(elemento: HTMLElement): (string | null)[] {
    return Array.from(elemento.querySelectorAll('nav[aria-label="Navegación principal"] a')).map((enlace) =>
      enlace.getAttribute('href'),
    );
  }

  function usuario(rol: RolUsuario): Sesion {
    return { autenticado: true, correo: 'usuario@alpes.com', rol, empresaId: 10, empresaNombre: 'Alpes Logistica' };
  }

  it('muestra la marca, el enlace para saltar al contenido y el área principal', async () => {
    const elemento = await crear(SESION_ANONIMA);

    expect(elemento.querySelector('.marca')?.textContent).toContain('Procesos Empresariales');
    expect(elemento.querySelector('.saltar')?.getAttribute('href')).toBe('#contenido');
    expect(elemento.querySelector('main#contenido router-outlet')).not.toBeNull();
  });

  it('a un visitante anónimo solo le ofrece iniciar sesión o registrar una empresa', async () => {
    const elemento = await crear(SESION_ANONIMA);

    expect(enlaces(elemento)).toEqual(['/iniciar-sesion', '/registro-empresa']);
    expect(elemento.querySelector('.usuario')).toBeNull();
  });

  it('a un editor le muestra la navegación completa, su usuario y el cierre de sesión', async () => {
    const elemento = await crear(usuario('EDITOR'));

    expect(enlaces(elemento)).toEqual(['/procesos', '/procesos/nuevo', '/roles-proceso', '/mi-empresa']);
    expect(elemento.querySelector('.usuario')?.textContent).toContain('usuario@alpes.com');
    expect(elemento.querySelector('.usuario')?.textContent).toContain('Editor · Alpes Logistica');
  });

  it('a un usuario de solo lectura no le ofrece crear procesos', async () => {
    const elemento = await crear(usuario('SOLO_LECTURA'));

    expect(enlaces(elemento)).toEqual(['/procesos', '/roles-proceso', '/mi-empresa']);
  });

  it('solo al administrador le ofrece la gestión de usuarios', async () => {
    const elemento = await crear(usuario('ADMINISTRADOR'));

    expect(enlaces(elemento)).toEqual(['/procesos', '/procesos/nuevo', '/roles-proceso', '/usuarios', '/mi-empresa']);
  });

  it('protege las rutas de usuarios con la sesión y el rol de administrador', () => {
    const protegidas = routes.find((ruta) => ruta.canActivateChild?.includes(sesionGuard))?.children ?? [];
    const usuarios = protegidas.filter((ruta) => ruta.path?.startsWith('usuarios'));

    expect(usuarios.map((ruta) => ruta.path)).toEqual(['usuarios', 'usuarios/nuevo', 'usuarios/:id']);
    usuarios.forEach((ruta) => {
      expect(ruta.canActivate).toEqual([rolGuard]);
      expect(ruta.data?.['roles']).toEqual(['ADMINISTRADOR']);
    });
  });

  it('cierra la sesión en el servidor y lleva al inicio de sesión', async () => {
    const elemento = await crear(usuario('ADMINISTRADOR'));
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    cerrar.mockReturnValue(of(undefined));

    elemento.querySelector<HTMLButtonElement>('.usuario__salir')!.click();

    expect(cerrar).toHaveBeenCalledTimes(1);
    expect(navegar).toHaveBeenCalledWith(['/iniciar-sesion'], { queryParams: { motivo: 'cerrada' } });
  });

  it('informa si el servidor no pudo cerrar la sesión', async () => {
    const elemento = await crear(usuario('ADMINISTRADOR'));
    cerrar.mockReturnValue(
      throwError(() => ({ tipo: 'sin-permiso', estado: 403, codigo: null, titulo: 'No tienes permiso para esta consulta', detalle: null })),
    );

    elemento.querySelector<HTMLButtonElement>('.usuario__salir')!.click();
    await fixture.whenStable();

    expect(elemento.querySelector('.cabecera__error')?.textContent).toContain('No se pudo cerrar la sesión');
  });
});

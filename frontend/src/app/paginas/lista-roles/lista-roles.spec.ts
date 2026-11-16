import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { Pagina } from '../../core/modelos/pagina';
import { FiltroRolesProceso, RolProcesoResumen } from '../../core/modelos/rol-proceso';
import { RolUsuario } from '../../core/modelos/sesion';
import { RolProcesoService } from '../../core/servicios/rol-proceso-service';
import { SesionService } from '../../core/servicios/sesion-service';
import { ListaRoles } from './lista-roles';

const SUPERVISOR: RolProcesoResumen = {
  id: 21,
  nombre: 'Supervisor',
  descripcion: 'Aprueba las solicitudes evaluadas',
  activo: true,
  enUso: true,
  puedeEliminar: false,
  procesos: [{ id: 5, nombre: 'Atención de solicitudes', lanes: 1, actividadesActivas: 1 }],
};

function pagina(content: RolProcesoResumen[], number = 0, totalPages = 1): Pagina<RolProcesoResumen> {
  return { content, page: { size: 10, number, totalElements: content.length + number * 10, totalPages } };
}

describe('ListaRoles', () => {
  let fixture: ComponentFixture<ListaRoles>;
  let elemento: HTMLElement;
  let listar: ReturnType<typeof vi.fn<(filtro: FiltroRolesProceso) => Observable<Pagina<RolProcesoResumen>>>>;

  async function crear(respuesta: Observable<Pagina<RolProcesoResumen>>, rol: RolUsuario = 'ADMINISTRADOR'): Promise<void> {
    listar = vi.fn<(filtro: FiltroRolesProceso) => Observable<Pagina<RolProcesoResumen>>>().mockReturnValue(respuesta);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: RolProcesoService, useValue: { listar } },
        { provide: SesionService, useValue: { tieneRol: (roles: readonly RolUsuario[]) => roles.includes(rol) } },
      ],
    });
    fixture = TestBed.createComponent(ListaRoles);
    elemento = fixture.nativeElement;
    await fixture.whenStable();
  }

  async function esperarFiltros(): Promise<void> {
    await new Promise((resolver) => setTimeout(resolver, 350));
    await fixture.whenStable();
  }

  function enlaceNuevo(): HTMLAnchorElement | undefined {
    return Array.from(elemento.querySelectorAll('a')).find((enlace) => enlace.textContent?.trim() === 'Nuevo rol');
  }

  it('consulta los roles activos de la primera página y muestra su uso', async () => {
    await crear(of(pagina([SUPERVISOR, { ...SUPERVISOR, id: 22, nombre: 'Analista', enUso: false, procesos: [] }])));

    expect(listar).toHaveBeenCalledWith({ q: null, visibilidad: 'ACTIVOS', page: 0 });
    const filas = elemento.querySelectorAll('tbody tr');
    expect(filas.length).toBe(2);
    expect(filas[0].querySelector('a')?.getAttribute('href')).toBe('/roles-proceso/21');
    expect(filas[0].textContent).toContain('1 proceso');
    expect(filas[1].textContent).toContain('Sin uso');
  });

  it('solo el administrador ve la opción de crear roles', async () => {
    await crear(of(pagina([SUPERVISOR])), 'ADMINISTRADOR');
    expect(enlaceNuevo()?.getAttribute('href')).toBe('/roles-proceso/nuevo');

    TestBed.resetTestingModule();
    await crear(of(pagina([SUPERVISOR])), 'EDITOR');

    expect(enlaceNuevo()).toBeUndefined();
  });

  it('filtra por nombre y visibilidad volviendo a la primera página', async () => {
    await crear(of(pagina([SUPERVISOR], 1, 3)));
    elemento.querySelector<HTMLButtonElement>('[aria-label="Página 3"]')!.click();
    await fixture.whenStable();
    expect(listar).toHaveBeenLastCalledWith({ q: null, visibilidad: 'ACTIVOS', page: 2 });

    const busqueda = elemento.querySelector<HTMLInputElement>('#filtro-rol-q')!;
    busqueda.value = ' Super ';
    busqueda.dispatchEvent(new Event('input'));
    const visibilidad = elemento.querySelector<HTMLSelectElement>('#filtro-rol-visibilidad')!;
    visibilidad.value = 'INACTIVOS';
    visibilidad.dispatchEvent(new Event('change'));
    await esperarFiltros();

    expect(listar).toHaveBeenLastCalledWith({ q: 'Super', visibilidad: 'INACTIVOS', page: 0 });
  });

  it('pide otra página al backend desde la paginación', async () => {
    await crear(of(pagina([SUPERVISOR], 0, 3)));

    Array.from(elemento.querySelectorAll<HTMLButtonElement>('.paginacion button'))
      .find((boton) => boton.textContent?.includes('Siguiente'))
      ?.click();
    await fixture.whenStable();

    expect(listar).toHaveBeenLastCalledWith({ q: null, visibilidad: 'ACTIVOS', page: 1 });
  });

  it('muestra el error del backend y permite reintentar', async () => {
    await crear(
      throwError(() => ({ tipo: 'no-autenticado', estado: 401, codigo: 'USUARIO_NO_AUTORIZADO', titulo: 'Se requiere iniciar sesión', detalle: null })),
    );

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('Se requiere iniciar sesión');
    listar.mockReturnValue(of(pagina([])));
    elemento.querySelector<HTMLButtonElement>('.aviso__accion')!.click();
    await fixture.whenStable();

    expect(elemento.textContent).toContain('No hay roles de proceso que coincidan');
  });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { FalloHttp } from '../../core/modelos/fallo-http';
import { Pagina } from '../../core/modelos/pagina';
import { FiltroProcesos, ProcesoResumen } from '../../core/modelos/proceso';
import { RolUsuario } from '../../core/modelos/sesion';
import { ProcesoService } from '../../core/servicios/proceso-service';
import { SesionService } from '../../core/servicios/sesion-service';
import { ListaProcesos } from './lista-procesos';

const PROCESO: ProcesoResumen = {
  id: 1,
  nombre: 'Atención de solicitudes',
  descripcion: 'Recepción y evaluación de solicitudes',
  categoria: 'Servicio al cliente',
  estado: 'PUBLICADO',
  eliminado: false,
  empresaPropietariaId: 7,
  empresaPropietariaNombre: 'Empresa de prueba',
  soloLectura: false,
};

function pagina(content: ProcesoResumen[], number = 0, totalPages = 1): Pagina<ProcesoResumen> {
  return { content, page: { size: 10, number, totalElements: content.length + number * 10, totalPages } };
}

describe('ListaProcesos', () => {
  let fixture: ComponentFixture<ListaProcesos>;
  let elemento: HTMLElement;
  let listar: ReturnType<typeof vi.fn<(filtro: FiltroProcesos) => Observable<Pagina<ProcesoResumen>>>>;

  async function crear(respuesta: Observable<Pagina<ProcesoResumen>>, rol: RolUsuario = 'EDITOR'): Promise<void> {
    listar = vi.fn<(filtro: FiltroProcesos) => Observable<Pagina<ProcesoResumen>>>().mockReturnValue(respuesta);
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: ProcesoService, useValue: { listar } },
        { provide: SesionService, useValue: { tieneRol: (roles: readonly RolUsuario[]) => roles.includes(rol) } },
      ],
    });
    fixture = TestBed.createComponent(ListaProcesos);
    elemento = fixture.nativeElement;
    await fixture.whenStable();
  }

  async function esperarFiltros(): Promise<void> {
    await new Promise((resolver) => setTimeout(resolver, 350));
    await fixture.whenStable();
  }

  it('ofrece crear procesos solo a los roles con permiso de escritura', async () => {
    await crear(of(pagina([PROCESO])), 'EDITOR');
    const enlaceNuevo = (): Element | undefined =>
      Array.from(elemento.querySelectorAll('a')).find((enlace) => enlace.textContent?.trim() === 'Nuevo proceso');
    expect(enlaceNuevo()?.getAttribute('href')).toBe('/procesos/nuevo');

    TestBed.resetTestingModule();
    await crear(of(pagina([PROCESO])), 'SOLO_LECTURA');

    expect(enlaceNuevo()).toBeUndefined();
  });

  it('consulta con los filtros por defecto del backend y muestra los procesos', async () => {
    await crear(of(pagina([PROCESO, { ...PROCESO, id: 2, nombre: 'Compras', soloLectura: true, estado: 'BORRADOR' }])));

    expect(listar).toHaveBeenCalledWith({
      q: null,
      estado: null,
      categoria: null,
      alcance: 'PROPIOS',
      visibilidad: 'ACTIVOS',
      page: 0,
    });
    const filas = elemento.querySelectorAll('tbody tr');
    expect(filas.length).toBe(2);
    expect(filas[0].querySelector('a')?.getAttribute('href')).toBe('/procesos/1');
    expect(filas[0].textContent).toContain('Publicado');
    expect(filas[1].textContent).toContain('Solo lectura');
    expect(filas[1].textContent).toContain('Borrador');
  });

  it('muestra un estado vacío sin inventar datos', async () => {
    await crear(of(pagina([])));

    expect(elemento.querySelector('table')).toBeNull();
    expect(elemento.textContent).toContain('No hay procesos que coincidan con los filtros');
  });

  it('explica el 401 del backend y permite reintentar', async () => {
    const fallo: FalloHttp = {
      tipo: 'no-autenticado',
      estado: 401,
      codigo: 'USUARIO_NO_AUTORIZADO',
      titulo: 'Se requiere iniciar sesión',
      detalle: 'Se requiere autenticacion',
    };
    await crear(throwError(() => fallo));

    const alerta = elemento.querySelector('[role="alert"]');
    expect(alerta?.textContent).toContain('Se requiere iniciar sesión');
    expect(alerta?.textContent).toContain('Inicia sesión para continuar');
    alerta!.querySelector<HTMLButtonElement>('button')!.click();
    await fixture.whenStable();
    expect(listar).toHaveBeenCalledTimes(2);
  });

  it('aplica los filtros tras la espera de escritura y vuelve a la primera página', async () => {
    await crear(of(pagina([PROCESO], 0, 3)));
    const buscador = elemento.querySelector<HTMLInputElement>('#filtro-q')!;
    const estado = elemento.querySelector<HTMLSelectElement>('#filtro-estado')!;

    buscador.value = '  compras ';
    buscador.dispatchEvent(new Event('input'));
    estado.value = 'BORRADOR';
    estado.dispatchEvent(new Event('change'));
    await esperarFiltros();

    expect(listar).toHaveBeenCalledTimes(2);
    expect(listar).toHaveBeenLastCalledWith(expect.objectContaining({ q: 'compras', estado: 'BORRADOR', page: 0 }));
  });

  it('pide la página seleccionada en la paginación', async () => {
    await crear(of(pagina([PROCESO], 0, 3)));

    elemento.querySelector<HTMLButtonElement>('[aria-label="Página 2"]')!.click();
    await fixture.whenStable();

    expect(listar).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }));
  });
});

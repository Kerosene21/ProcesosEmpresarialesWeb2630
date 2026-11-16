import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { diagramaCompleto, pool } from '../../../testing/diagramas';
import { DiagramaProceso, PoolRespuesta } from '../../core/modelos/diagrama';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { RecolocarNodos } from '../../core/modelos/movimiento';
import { CrearPool, EditarPool } from '../../core/modelos/pool';
import { PoolService } from '../../core/servicios/pool-service';
import { ProcesoService } from '../../core/servicios/proceso-service';
import { PanelPools, PermisosPools, contenidoDePool } from './panel-pools';

const TODOS: PermisosPools = { crearPool: true, editarPool: true, eliminarPool: true };

function fallo(tipo: FalloHttp['tipo'], estado: number, codigo: string, detalle: string): FalloHttp {
  return { tipo, estado, codigo, titulo: 'Error del servidor', detalle };
}

describe('PanelPools', () => {
  let fixture: ComponentFixture<PanelPools>;
  let elemento: HTMLElement;
  let cambios: number;
  let rechazos: number;
  const crearPool = vi.fn<(procesoId: number, datos: CrearPool) => Observable<PoolRespuesta>>();
  const editar = vi.fn<(procesoId: number, poolId: number, datos: EditarPool) => Observable<PoolRespuesta>>();
  const eliminar = vi.fn<(procesoId: number, poolId: number) => Observable<void>>();
  const recolocarNodos = vi.fn<(procesoId: number, datos: RecolocarNodos) => Observable<void>>();

  async function crear(permisos: PermisosPools = TODOS, diagrama: DiagramaProceso = diagramaCompleto()): Promise<void> {
    [crearPool, editar, eliminar, recolocarNodos].forEach((simulado) => simulado.mockReset());
    recolocarNodos.mockReturnValue(of(undefined));
    TestBed.configureTestingModule({
      providers: [
        { provide: PoolService, useValue: { crear: crearPool, editar, eliminar } },
        { provide: ProcesoService, useValue: { recolocarNodos } },
      ],
    });
    fixture = TestBed.createComponent(PanelPools);
    elemento = fixture.nativeElement;
    cambios = 0;
    rechazos = 0;
    fixture.componentInstance.cambio.subscribe(() => cambios++);
    fixture.componentInstance.rechazo.subscribe(() => rechazos++);
    fixture.componentRef.setInput('procesoId', 5);
    fixture.componentRef.setInput('diagrama', diagrama);
    fixture.componentRef.setInput('permisos', permisos);
    await fixture.whenStable();
  }

  function boton(etiqueta: string): HTMLButtonElement | undefined {
    return Array.from(elemento.querySelectorAll<HTMLButtonElement>('button')).find(
      (candidato) => candidato.getAttribute('aria-label') === etiqueta || candidato.textContent?.trim() === etiqueta,
    );
  }

  async function escribir(selector: string, valor: string): Promise<void> {
    const campo = elemento.querySelector<HTMLInputElement>(selector)!;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function marcar(selector: string): Promise<void> {
    elemento.querySelector<HTMLInputElement>(selector)!.click();
    await fixture.whenStable();
  }

  async function elegir(selector: string, indice: number): Promise<void> {
    const lista = elemento.querySelector<HTMLSelectElement>(selector)!;
    lista.selectedIndex = indice;
    lista.dispatchEvent(new Event('change'));
    await fixture.whenStable();
  }

  async function enviar(selector: string): Promise<void> {
    elemento.querySelector(selector)!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  function textoDe(poolId: number): string {
    return elemento.querySelector(`[data-pool="${poolId}"]`)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }

  it('cuenta el contenido real de cada pool a partir del diagrama', () => {
    const diagrama = diagramaCompleto();

    expect(contenidoDePool(diagrama, 80)).toEqual({ lanes: 2, actividades: 3, gateways: 1, eventos: 2, flujosEntrantes: 1 });
    expect(contenidoDePool(diagrama, 81)).toEqual({ lanes: 0, actividades: 0, gateways: 0, eventos: 1, flujosEntrantes: 0 });
    expect(contenidoDePool(diagrama, 82)).toEqual({ lanes: 0, actividades: 0, gateways: 0, eventos: 0, flujosEntrantes: 1 });
  });

  it('distingue el propietario, los participantes, los externos y las cajas negras', async () => {
    const diagrama = diagramaCompleto();
    diagrama.pools[1] = { ...diagrama.pools[1], tipo: 'PARTICIPANTE', empresaId: 9, empresaNombre: 'Andes SAS' };
    await crear(TODOS, diagrama);

    const nombres = Array.from(elemento.querySelectorAll('.panel-pools__datos strong')).map((nombre) => nombre.textContent);
    const chips = (poolId: number) =>
      Array.from(elemento.querySelectorAll(`[data-pool="${poolId}"] .chip`)).map((chip) => chip.textContent?.trim());

    expect(nombres).toEqual(['1. Empresa de prueba', '2. Cliente', '3. Servicio de correo']);
    expect(chips(80)).toEqual(['Propietario']);
    expect(chips(81)).toEqual(['Participante']);
    expect(chips(82)).toEqual(['Externo', 'Caja negra']);
    expect(textoDe(80)).toContain('Empresa: Empresa de prueba · 2 lanes · 3 actividades · 1 gateway · 2 eventos · recibe 1 flujo de mensaje');
    expect(textoDe(81)).toContain('Empresa: Andes SAS');
    expect(textoDe(82)).toContain('Participante no registrado · Caja negra: sin lanes ni elementos internos');
  });

  it('informa cuando el diagrama no tiene pools activos', async () => {
    const diagrama = diagramaCompleto();
    diagrama.pools = [];
    await crear(TODOS, diagrama);

    expect(elemento.textContent).toContain('El diagrama no tiene pools activos');
  });

  it('crea un pool externo de caja negra y avisa para recargar el diagrama', async () => {
    await crear();
    crearPool.mockReturnValue(of(pool(83, 'Banco', 4, { cajaNegra: true })));

    await escribir('#nuevo-pool-nombre', '  Banco  ');
    await marcar('.panel-pools__creacion input[type="checkbox"]');
    await enviar('.panel-pools__creacion');

    expect(crearPool).toHaveBeenCalledWith(5, { nombre: 'Banco', tipo: 'EXTERNO', cajaNegra: true, empresaParticipanteId: null });
    expect(cambios).toBe(1);
    expect(elemento.querySelector('[role="status"]')?.textContent).toContain('El pool «Banco» se creó');
    expect(elemento.querySelector<HTMLInputElement>('#nuevo-pool-nombre')?.value).toBe('');
  });

  it('exige el nombre y la empresa de un pool participante antes de llamar a la API', async () => {
    await crear();
    crearPool.mockReturnValue(of(pool(83, 'Proveedor', 4, { tipo: 'PARTICIPANTE', empresaId: 9, empresaNombre: 'Andes SAS' })));

    await enviar('.panel-pools__creacion');
    expect(elemento.querySelector('.panel-pools__creacion .campo__error')?.textContent).toBe('El nombre del pool es obligatorio');

    await escribir('#nuevo-pool-nombre', 'Proveedor');
    await elegir('#nuevo-pool-tipo', 0);
    expect(elemento.querySelector('#ayuda-nuevo-pool-tipo')?.textContent).toContain('Otra empresa registrada');
    await enviar('.panel-pools__creacion');
    expect(crearPool).not.toHaveBeenCalled();
    expect(elemento.textContent).toContain('Indica el identificador de la empresa participante');

    await escribir('#nuevo-pool-empresa', '9');
    await enviar('.panel-pools__creacion');

    expect(crearPool).toHaveBeenCalledWith(5, { nombre: 'Proveedor', tipo: 'PARTICIPANTE', cajaNegra: false, empresaParticipanteId: 9 });
  });

  it('muestra el rechazo del backend cuando la empresa participante no existe', async () => {
    await crear();
    crearPool.mockReturnValue(throwError(() => fallo('no-encontrado', 404, 'RECURSO_NO_ENCONTRADO', 'La empresa no existe')));

    await escribir('#nuevo-pool-nombre', 'Proveedor');
    await elegir('#nuevo-pool-tipo', 0);
    await escribir('#nuevo-pool-empresa', '999');
    await enviar('.panel-pools__creacion');

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('La empresa no existe');
    expect(cambios).toBe(0);
  });

  it('edita el nombre de un pool y lo convierte en caja negra', async () => {
    await crear();
    editar.mockReturnValue(of(pool(82, 'Correo corporativo', 3, { cajaNegra: true })));

    boton('Editar el pool Servicio de correo')!.click();
    await fixture.whenStable();
    await escribir('#pool-nombre-82', 'Correo corporativo');
    await enviar('[data-pool="82"] form');

    expect(editar).toHaveBeenCalledWith(5, 82, { nombre: 'Correo corporativo', cajaNegra: true, empresaParticipanteId: null });
    expect(cambios).toBe(1);
    expect(elemento.querySelector('[data-pool="82"] form')).toBeNull();
  });

  it('no deja convertir el pool propietario en caja negra', async () => {
    await crear();
    editar.mockReturnValue(of(pool(80, 'Empresa principal', 1)));

    boton('Editar el pool Empresa de prueba')!.click();
    await fixture.whenStable();
    expect(elemento.querySelector<HTMLInputElement>('[data-pool="80"] input[type="checkbox"]')?.disabled).toBe(true);
    expect(elemento.textContent).toContain('El pool propietario no puede ser una caja negra');
    await escribir('#pool-nombre-80', 'Empresa principal');
    await enviar('[data-pool="80"] form');

    expect(editar).toHaveBeenCalledWith(5, 80, { nombre: 'Empresa principal', cajaNegra: false, empresaParticipanteId: null });
  });

  it('muestra junto a la caja negra el rechazo de un pool con contenido', async () => {
    await crear();
    editar.mockReturnValue(
      throwError(() => fallo('conflicto', 409, 'POOL_CON_CONTENIDO', "El pool 'Cliente' no puede convertirse en caja negra: contiene 1 eventos activos")),
    );

    boton('Editar el pool Cliente')!.click();
    await fixture.whenStable();
    await marcar('[data-pool="81"] input[type="checkbox"]');
    await enviar('[data-pool="81"] form');

    const errores = Array.from(elemento.querySelectorAll('[data-pool="81"] .campo__error')).map((error) => error.textContent);
    expect(errores.at(-1)).toContain('contiene 1 eventos activos');
    expect(errores[0]).toBe('');
    expect(cambios).toBe(0);
  });

  it('solo permite eliminar pools adicionales vacíos y sin mensajes entrantes', async () => {
    const diagrama = diagramaCompleto();
    diagrama.pools.push(pool(83, 'Banco', 4, { cajaNegra: true }));
    await crear(TODOS, diagrama);
    eliminar.mockReturnValue(of(undefined));

    expect(boton('Eliminar el pool Empresa de prueba')?.disabled).toBe(true);
    expect(boton('Eliminar el pool Empresa de prueba')?.title).toContain('no se elimina');
    expect(boton('Eliminar el pool Cliente')?.disabled).toBe(true);
    expect(boton('Eliminar el pool Servicio de correo')?.title).toContain('Recibe flujos de mensaje');
    boton('Eliminar el pool Banco')!.click();
    await fixture.whenStable();
    expect(elemento.querySelector('[role="alertdialog"]')?.textContent).toContain('«Banco»');
    elemento.querySelector<HTMLButtonElement>('[role="alertdialog"] .boton--peligro')!.click();
    await fixture.whenStable();

    expect(eliminar).toHaveBeenCalledWith(5, 83);
    expect(cambios).toBe(1);
    expect(elemento.querySelector('[role="alertdialog"]')).toBeNull();
    expect(elemento.querySelector('[role="status"]')?.textContent).toContain('El pool «Banco» se eliminó');
  });

  it('muestra en el diálogo el rechazo del backend al eliminar', async () => {
    const diagrama = diagramaCompleto();
    diagrama.pools.push(pool(83, 'Banco', 4));
    await crear(TODOS, diagrama);
    eliminar.mockReturnValue(
      throwError(() => fallo('conflicto', 409, 'POOL_CON_CONTENIDO', "El pool 'Banco' no se puede eliminar: es destino de 1 flujos de mensaje activos")),
    );

    boton('Eliminar el pool Banco')!.click();
    await fixture.whenStable();
    elemento.querySelector<HTMLButtonElement>('[role="alertdialog"] .boton--peligro')!.click();
    await fixture.whenStable();

    expect(elemento.querySelector('[role="alertdialog"] [role="alert"]')?.textContent).toContain('es destino de 1 flujos');
    expect(cambios).toBe(0);
  });

  it('sin permisos de estructura solo muestra los pools', async () => {
    await crear({ crearPool: false, editarPool: false, eliminarPool: false });

    expect(elemento.querySelectorAll('.panel-pools__pool').length).toBe(3);
    expect(elemento.querySelector('.panel-pools__creacion')).toBeNull();
    expect(elemento.querySelector('.panel-pools__acciones')).toBeNull();
    expect(elemento.textContent).toContain('Tu rol no tiene permisos de estructura');
  });

  it('ofrece solo las operaciones concedidas al editor', async () => {
    await crear({ crearPool: false, editarPool: true, eliminarPool: false });

    expect(boton('Editar el pool Cliente')).toBeDefined();
    expect(boton('Eliminar el pool Cliente')).toBeUndefined();
    expect(elemento.querySelector('.panel-pools__creacion')).toBeNull();
  });

  it('ante un 403 muestra el motivo y pide reconsultar los permisos', async () => {
    await crear();
    crearPool.mockReturnValue(
      throwError(() => fallo('sin-permiso', 403, 'USUARIO_SIN_PERMISO', 'El rol EDITOR no tiene permiso para crear pools en este proceso')),
    );

    await escribir('#nuevo-pool-nombre', 'Banco');
    await enviar('.panel-pools__creacion');

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('no tiene permiso para crear pools');
    expect(rechazos).toBe(1);
    expect(cambios).toBe(0);
  });

  describe('recolocación de los pools inferiores', () => {
    function diagramaConPoolsIntermedios(): DiagramaProceso {
      const diagrama = diagramaCompleto();
      diagrama.pools = [
        pool(80, 'Empresa de prueba', 1),
        pool(83, 'Banco', 2, { cajaNegra: true }),
        pool(81, 'Cliente', 3),
        pool(82, 'Servicio de correo', 4, { cajaNegra: true }),
      ];
      diagrama.messageThrows = [{ ...diagrama.messageThrows[0], posicionY: 460 }];
      return diagrama;
    }

    it('al quitar la caja negra a un pool intermedio baja el contenido de los pools siguientes', async () => {
      await crear(TODOS, diagramaConPoolsIntermedios());
      editar.mockReturnValue(of(pool(83, 'Banco', 2)));

      boton('Editar el pool Banco')!.click();
      await fixture.whenStable();
      await marcar('[data-pool="83"] form input[type="checkbox"]');
      await enviar('[data-pool="83"] form');

      expect(editar).toHaveBeenCalledWith(5, 83, { nombre: 'Banco', cajaNegra: false, empresaParticipanteId: null });
      expect(recolocarNodos).toHaveBeenCalledWith(5, { nodos: [{ tipo: 'EVENTO', id: 6, posicionX: 60, posicionY: 520 }] });
      expect(elemento.querySelector('[role="status"]')?.textContent).toContain(
        'Se recolocó un elemento del diagrama para conservar su lugar en su pool.',
      );
      expect(cambios).toBe(1);
    });

    it('al eliminar un pool intermedio sube el contenido de los pools siguientes', async () => {
      await crear(TODOS, diagramaConPoolsIntermedios());
      eliminar.mockReturnValue(of(undefined));

      boton('Eliminar el pool Banco')!.click();
      await fixture.whenStable();
      elemento.querySelector<HTMLButtonElement>('[role="alertdialog"] .boton--peligro')!.click();
      await fixture.whenStable();

      expect(eliminar).toHaveBeenCalledWith(5, 83);
      expect(recolocarNodos).toHaveBeenCalledWith(5, { nodos: [{ tipo: 'EVENTO', id: 6, posicionX: 60, posicionY: 340 }] });
      expect(cambios).toBe(1);
    });

    it('si la recolocación falla conserva el cambio del pool y lo explica', async () => {
      await crear(TODOS, diagramaConPoolsIntermedios());
      eliminar.mockReturnValue(of(undefined));
      recolocarNodos.mockReturnValue(throwError(() => fallo('sin-permiso', 403, 'USUARIO_SIN_PERMISO', 'Solo un administrador o editor puede modificar el diagrama')));

      boton('Eliminar el pool Banco')!.click();
      await fixture.whenStable();
      elemento.querySelector<HTMLButtonElement>('[role="alertdialog"] .boton--peligro')!.click();
      await fixture.whenStable();

      expect(elemento.querySelector('[role="status"]')?.textContent?.trim()).toBe(
        'El pool «Banco» se eliminó. Los demás pools se reordenaron.',
      );
      expect(elemento.textContent).toContain('no se pudieron recolocar los elementos del diagrama');
      expect(elemento.textContent).toContain('Solo un administrador o editor puede modificar el diagrama');
      expect(cambios).toBe(1);
    });
  });

  it('cierra la confirmación de eliminación si se revoca el permiso', async () => {
    const diagrama = diagramaCompleto();
    diagrama.pools.push(pool(83, 'Banco', 4, { cajaNegra: true }));
    await crear(TODOS, diagrama);

    boton('Eliminar el pool Banco')!.click();
    await fixture.whenStable();
    expect(elemento.querySelector('[role="alertdialog"]')).not.toBeNull();
    fixture.componentRef.setInput('permisos', { crearPool: true, editarPool: true, eliminarPool: false });
    await fixture.whenStable();

    expect(elemento.querySelector('[role="alertdialog"]')).toBeNull();
    expect(eliminar).not.toHaveBeenCalled();
  });
});

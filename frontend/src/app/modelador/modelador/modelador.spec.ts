import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { PROCESO_PRUEBA, actividad, diagramaCompleto, diagramaInicial } from '../../../testing/diagramas';
import { DiagramaProceso } from '../../core/modelos/diagrama';
import { ActividadService } from '../../core/servicios/actividad-service';
import { ArcoService } from '../../core/servicios/arco-service';
import { GatewayService } from '../../core/servicios/gateway-service';
import { Modelador, PermisosEdicion } from './modelador';

describe('Modelador', () => {
  let fixture: ComponentFixture<Modelador>;
  let elemento: HTMLElement;
  let cambios: number;
  const crearActividad = vi.fn();
  const previsualizarGateway = vi.fn();

  async function crear(diagrama: DiagramaProceso, edicion: PermisosEdicion | null = null): Promise<void> {
    crearActividad.mockReset();
    previsualizarGateway.mockReset().mockReturnValue(of({ ...diagramaCompleto().gateways[0], advertencias: [] }));
    TestBed.configureTestingModule({
      providers: [
        { provide: ActividadService, useValue: { crear: crearActividad } },
        { provide: GatewayService, useValue: { previsualizarEliminacion: previsualizarGateway } },
        { provide: ArcoService, useValue: {} },
      ],
    });
    fixture = TestBed.createComponent(Modelador);
    elemento = fixture.nativeElement;
    cambios = 0;
    fixture.componentInstance.cambio.subscribe(() => cambios++);
    fixture.componentRef.setInput('proceso', PROCESO_PRUEBA);
    fixture.componentRef.setInput('diagrama', diagrama);
    fixture.componentRef.setInput('edicion', edicion);
    await fixture.whenStable();
  }

  function boton(texto: string): HTMLButtonElement | undefined {
    return Array.from(elemento.querySelectorAll<HTMLButtonElement>('button')).find(
      (candidato) => candidato.textContent?.trim() === texto,
    );
  }

  async function pulsar(destino: Element, opciones: MouseEventInit = {}): Promise<void> {
    destino.dispatchEvent(new MouseEvent('click', { bubbles: true, ...opciones }));
    await fixture.whenStable();
  }

  async function pulsarLienzo(x: number, y: number): Promise<void> {
    await pulsar(elemento.querySelector('svg')!, { clientX: x + 76, clientY: y + 16 });
  }

  function indicacion(): string {
    return elemento.querySelector('.barra__ayuda')?.textContent?.trim() ?? '';
  }

  function elegida(selector: string): string | undefined {
    return elemento.querySelector<HTMLSelectElement>(selector)!.selectedOptions[0]?.textContent?.trim();
  }

  function botonDeLista(texto: string): HTMLButtonElement {
    return Array.from(elemento.querySelectorAll<HTMLButtonElement>('.modelador__elemento')).find(
      (boton) => boton.textContent?.trim() === texto,
    )!;
  }

  it('avisa que el diagrama está vacío y aun así dibuja el pool y la lane iniciales', async () => {
    await crear(diagramaInicial());

    expect(elemento.querySelector('[role="status"]')?.textContent).toContain('todavía no tiene actividades');
    expect(elemento.querySelector('.lane__nombre')?.textContent?.trim()).toBe('General');
    expect(elemento.querySelector('.modelador__elemento')).toBeNull();
  });

  it('lista los elementos agrupados como alternativa accesible al lienzo', async () => {
    await crear(diagramaCompleto());

    expect(Array.from(elemento.querySelectorAll('.modelador__grupo')).map((grupo) => grupo.textContent)).toEqual([
      'Actividades',
      'Gateways',
      'Arcos',
      'Eventos',
    ]);
    expect(elemento.querySelectorAll('.modelador__elemento').length).toBe(13);
  });

  it('selecciona desde la lista, resalta en el lienzo y muestra el panel', async () => {
    await crear(diagramaCompleto());

    botonDeLista('Evaluar solicitud').click();
    await fixture.whenStable();

    expect(botonDeLista('Evaluar solicitud').getAttribute('aria-pressed')).toBe('true');
    expect(elemento.querySelector('.nodo--seleccionado')?.getAttribute('aria-label')).toContain('Evaluar solicitud');
    expect(elemento.querySelector('.panel__nombre')?.textContent).toBe('Evaluar solicitud');
  });

  it('cambia la selección desde el lienzo con el teclado y la limpia al pulsar el fondo', async () => {
    await crear(diagramaCompleto());

    elemento.querySelector('g[appNodoGateway]')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter' }));
    await fixture.whenStable();
    expect(elemento.querySelector('.panel__nombre')?.textContent).toBe('Gateway EXCLUSIVO #4');

    elemento.querySelectorAll('g[appArcoSecuencia]')[1].dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await fixture.whenStable();
    expect(elemento.querySelector('.panel__nombre')?.textContent).toBe('Registrar solicitud → Evaluar solicitud');
    expect(elemento.querySelectorAll('.nodo--seleccionado').length).toBe(0);

    elemento.querySelector('svg')!.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await fixture.whenStable();
    expect(elemento.querySelector('.panel__nombre')).toBeNull();
  });

  it('descarta la selección si el elemento ya no viene en el diagrama actualizado', async () => {
    await crear(diagramaCompleto());
    botonDeLista('Aprobar solicitud').click();
    await fixture.whenStable();

    const actualizado = diagramaCompleto();
    actualizado.actividades = actualizado.actividades.filter((actual) => actual.id !== 3);
    fixture.componentRef.setInput('diagrama', actualizado);
    await fixture.whenStable();

    expect(elemento.querySelector('.panel__nombre')).toBeNull();
    expect(elemento.querySelectorAll('.nodo--seleccionado').length).toBe(0);
  });

  it('muestra las advertencias de los datos inconsistentes', async () => {
    const diagrama = diagramaInicial();
    diagrama.actividades = [actividad(1, 'Huérfana', 99, 100, 40)];

    await crear(diagrama);

    expect(elemento.querySelector('.modelador__advertencias')?.textContent).toContain(
      '«Huérfana» pertenece a una lane que no aparece en el diagrama',
    );
    expect(elemento.querySelectorAll('g[appNodoActividad]').length).toBe(1);
  });

  it('sin permisos de edición no muestra herramientas ni acciones sobre el elemento', async () => {
    await crear(diagramaCompleto());
    botonDeLista('Evaluar solicitud').click();
    await fixture.whenStable();

    expect(elemento.querySelector('[role="toolbar"]')).toBeNull();
    expect(boton('Editar')).toBeUndefined();
    expect(boton('Eliminar')).toBeUndefined();
  });

  it('ofrece herramientas y edición al rol de escritura y la eliminación solo al administrador', async () => {
    await crear(diagramaCompleto(), { escribir: true, eliminar: false });
    botonDeLista('Evaluar solicitud').click();
    await fixture.whenStable();

    expect(elemento.querySelector('[role="toolbar"]')).not.toBeNull();
    expect(boton('Editar')).toBeDefined();
    expect(boton('Eliminar')).toBeUndefined();

    fixture.componentRef.setInput('edicion', { escribir: true, eliminar: true });
    await fixture.whenStable();
    expect(boton('Eliminar')).toBeDefined();

    botonDeLista('Message Catch: Solicitud de servicio').click();
    await fixture.whenStable();
    expect(boton('Editar')).toBeUndefined();
    expect(boton('Eliminar')).toBeUndefined();
  });

  it('propone la lane y la posición del punto pulsado con la herramienta de actividad', async () => {
    await crear(diagramaCompleto(), { escribir: true, eliminar: false });
    boton('Actividad')!.click();
    await fixture.whenStable();

    expect(elemento.querySelector('.lienzo--colocando')).not.toBeNull();
    await pulsarLienzo(300, 210);

    expect(elemento.querySelector('.formulario__titulo')?.textContent?.trim()).toBe('Nueva actividad');
    expect(elegida('[formControlName="laneId"]')).toBe('Supervisor');
    expect(elemento.querySelector<HTMLInputElement>('[formControlName="posicionX"]')!.value).toBe('235');
    expect(elemento.querySelector<HTMLInputElement>('[formControlName="posicionY"]')!.value).toBe('187');

    await pulsarLienzo(300, 300);
    expect(indicacion()).toContain('Ese punto no está dentro de una lane');
  });

  it('ubica un gateway en el pool pulsado', async () => {
    await crear(diagramaCompleto(), { escribir: true, eliminar: false });
    boton('Gateway')!.click();
    await pulsarLienzo(300, 400);

    expect(elemento.querySelector('.formulario__titulo')?.textContent?.trim()).toBe('Nuevo gateway');
    expect(elegida('[formControlName="poolId"]')).toBe('Cliente');
    expect(elemento.querySelector<HTMLInputElement>('[formControlName="posicionY"]')!.value).toBe('377');
  });

  it('elige el origen y el destino de un arco en el lienzo respetando las reglas de conexión', async () => {
    await crear(diagramaCompleto(), { escribir: true, eliminar: false });
    boton('Arco')!.click();
    await fixture.whenStable();
    const [registrar, , aprobar] = Array.from(elemento.querySelectorAll('g[appNodoActividad]'));

    await pulsar(registrar);
    expect(registrar.classList).toContain('nodo--marcado');
    expect(indicacion()).toBe('Origen: «Registrar solicitud». Pulsa el nodo de destino.');
    expect(elemento.querySelector('.panel__nombre')).toBeNull();

    await pulsar(elemento.querySelectorAll('g[appNodoEvento]')[1]);
    expect(indicacion()).toContain('están en pools distintos');

    await pulsar(aprobar);
    expect(elemento.querySelector('.formulario__titulo')?.textContent?.trim()).toBe('Nuevo arco');
    expect(elegida('[formControlName="origen"]')).toBe('Registrar solicitud');
    expect(elegida('[formControlName="destino"]')).toBe('Aprobar solicitud');
    expect(elemento.querySelectorAll('.nodo--marcado').length).toBe(0);
  });

  it('crea con el formulario sin usar el lienzo, avisa del cambio y selecciona lo que devuelve el servidor', async () => {
    await crear(diagramaCompleto(), { escribir: true, eliminar: false });
    const creada = actividad(9, 'Archivar solicitud', 11, 896, 47);
    crearActividad.mockReturnValue(of(creada));
    boton('Nueva actividad')!.click();
    await fixture.whenStable();

    expect(elemento.querySelector<HTMLInputElement>('[formControlName="posicionX"]')!.value).toBe('896');
    const nombre = elemento.querySelector<HTMLInputElement>('[formControlName="nombre"]')!;
    nombre.value = 'Archivar solicitud';
    nombre.dispatchEvent(new Event('input'));
    const tipo = elemento.querySelector<HTMLSelectElement>('[formControlName="tipo"]')!;
    tipo.selectedIndex = 1;
    tipo.dispatchEvent(new Event('change'));
    elemento.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    expect(crearActividad).toHaveBeenCalledWith(5, expect.objectContaining({ laneId: 11, posicionX: 896, posicionY: 47 }));
    expect(cambios).toBe(1);
    expect(elemento.querySelector('form')).toBeNull();
    expect(elemento.querySelector('.modelador__exito')?.textContent).toContain('«Archivar solicitud» se guardó en el servidor');
    expect(elemento.querySelector('.panel__nombre')).toBeNull();

    const actualizado = diagramaCompleto();
    actualizado.actividades.push(creada);
    fixture.componentRef.setInput('diagrama', actualizado);
    await fixture.whenStable();

    expect(elemento.querySelector('.panel__nombre')?.textContent).toBe('Archivar solicitud');
  });

  it('abre la edición y la confirmación de eliminación del elemento seleccionado y permite cancelarlas', async () => {
    await crear(diagramaCompleto(), { escribir: true, eliminar: true });
    botonDeLista('Exclusivo #4').click();
    await fixture.whenStable();

    boton('Editar')!.click();
    await fixture.whenStable();
    expect(elemento.querySelector('.formulario__titulo')?.textContent?.trim()).toBe('Editar Gateway EXCLUSIVO #4');
    boton('Cancelar')!.click();
    await fixture.whenStable();
    expect(elemento.querySelector('form')).toBeNull();

    boton('Eliminar')!.click();
    await fixture.whenStable();
    expect(previsualizarGateway).toHaveBeenCalledWith(5, 4);
    expect(elemento.querySelector('[role="alertdialog"] h2')?.textContent).toBe('¿Eliminar el gateway?');
    boton('Cancelar')!.click();
    await fixture.whenStable();
    expect(elemento.querySelector('[role="alertdialog"]')).toBeNull();
    expect(cambios).toBe(0);
  });
});

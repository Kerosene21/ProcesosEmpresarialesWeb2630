import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { PROCESO_PRUEBA, actividad, diagramaCompleto, diagramaInicial } from '../../../testing/diagramas';
import { DiagramaProceso } from '../../core/modelos/diagrama';
import { ActividadService } from '../../core/servicios/actividad-service';
import { ArcoService } from '../../core/servicios/arco-service';
import { EnvioExternoService } from '../../core/servicios/envio-externo-service';
import { GatewayService } from '../../core/servicios/gateway-service';
import { MessageCatchService } from '../../core/servicios/message-catch-service';
import { MessageThrowService } from '../../core/servicios/message-throw-service';
import { Punto } from '../geometria';
import { Modelador, PermisosEdicion } from './modelador';

describe('Modelador', () => {
  let fixture: ComponentFixture<Modelador>;
  let elemento: HTMLElement;
  let cambios: number;
  const crearActividad = vi.fn();
  const previsualizarGateway = vi.fn();
  const crearThrow = vi.fn();
  const previsualizarCatch = vi.fn();
  const moverActividad = vi.fn();
  const moverGateway = vi.fn();
  const moverCatch = vi.fn();

  async function crear(diagrama: DiagramaProceso, edicion: PermisosEdicion | null = null): Promise<void> {
    crearActividad.mockReset();
    previsualizarGateway.mockReset().mockReturnValue(of({ ...diagramaCompleto().gateways[0], advertencias: [] }));
    crearThrow.mockReset();
    moverActividad.mockReset();
    moverGateway.mockReset();
    moverCatch.mockReset();
    previsualizarCatch.mockReset().mockReturnValue(
      of({
        ...diagramaCompleto().messageCatches[0],
        advertencias: ["El Message Throw 'Solicitud de servicio' quedará sin Message Catch homólogo en el pool 'Empresa de prueba'."],
      }),
    );
    TestBed.configureTestingModule({
      providers: [
        { provide: ActividadService, useValue: { crear: crearActividad, mover: moverActividad } },
        { provide: GatewayService, useValue: { previsualizarEliminacion: previsualizarGateway, mover: moverGateway } },
        { provide: ArcoService, useValue: {} },
        { provide: MessageThrowService, useValue: { crear: crearThrow } },
        { provide: MessageCatchService, useValue: { previsualizarEliminacion: previsualizarCatch, mover: moverCatch } },
        { provide: EnvioExternoService, useValue: {} },
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
    expect(boton('Editar')).toBeDefined();
    expect(boton('Eliminar')).toBeDefined();
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

  it('ubica un Message Catch en el pool pulsado y lo rechaza en un pool de caja negra', async () => {
    await crear(diagramaCompleto(), { escribir: true, eliminar: false });
    boton('Message Catch')!.click();
    await fixture.whenStable();

    expect(elemento.querySelector('.lienzo--colocando')).not.toBeNull();
    await pulsarLienzo(300, 210);
    expect(elemento.querySelector('.formulario__titulo')?.textContent?.trim()).toBe('Nuevo Message Catch');
    expect(elegida('[formControlName="poolId"]')).toBe('Empresa de prueba (propietario)');
    expect(elemento.querySelector<HTMLInputElement>('[formControlName="posicionX"]')!.value).toBe('282');
    expect(elemento.querySelector<HTMLInputElement>('[formControlName="posicionY"]')!.value).toBe('192');

    boton('Cancelar')!.click();
    await fixture.whenStable();
    await pulsarLienzo(300, 540);
    expect(indicacion()).toContain('no está dentro de un pool que admita eventos');
    expect(elemento.querySelector('form')).toBeNull();
  });

  it('exige otro pool para un Message Throw y un sistema externo para un envío externo', async () => {
    await crear(diagramaInicial(), { escribir: true, eliminar: false });

    boton('Nuevo Message Throw')!.click();
    await fixture.whenStable();
    expect(indicacion()).toContain('necesita otro pool como receptor');
    boton('Nuevo envío externo')!.click();
    await fixture.whenStable();
    expect(indicacion()).toContain('pool externo de caja negra');
    expect(elemento.querySelector('form')).toBeNull();

    TestBed.resetTestingModule();
    await crear(diagramaCompleto(), { escribir: true, eliminar: false });
    boton('Nuevo envío externo')!.click();
    await fixture.whenStable();
    expect(elemento.querySelector('.formulario__titulo')?.textContent?.trim()).toBe('Nuevo envío externo');
    expect(elegida('[formControlName="poolDestinoId"]')).toBe('Servicio de correo (externo, caja negra)');
  });

  it('guarda un Message Throw, avisa del cambio y resume las advertencias del servidor', async () => {
    await crear(diagramaCompleto(), { escribir: true, eliminar: false });
    const creado = {
      ...diagramaCompleto().messageThrows[0],
      id: 12,
      nombreMensaje: 'Pago',
      advertencias: ["No existe un Message Catch 'Pago' en el pool destino 'Cliente': el mensaje no tiene un receptor modelado."],
    };
    crearThrow.mockReturnValue(of(creado));
    boton('Nuevo Message Throw')!.click();
    await fixture.whenStable();
    for (const [campo, valor] of [
      ['nombreMensaje', 'Pago'],
      ['contenido', 'numeroFactura'],
    ]) {
      const control = elemento.querySelector<HTMLInputElement>(`[formControlName="${campo}"]`)!;
      control.value = valor;
      control.dispatchEvent(new Event('input'));
    }
    const destino = elemento.querySelector<HTMLSelectElement>('[formControlName="poolDestinoId"]')!;
    destino.selectedIndex = 1;
    destino.dispatchEvent(new Event('change'));
    elemento.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    expect(crearThrow).toHaveBeenCalledWith(5, expect.objectContaining({ nombreMensaje: 'Pago', poolOrigenId: 80, poolDestinoId: 81 }));
    expect(cambios).toBe(1);
    expect(elemento.querySelector('.modelador__exito')?.textContent).toContain('El servidor informa una advertencia');
  });

  it('edita y elimina eventos seleccionados con la vista previa del servidor', async () => {
    await crear(diagramaCompleto(), { escribir: true, eliminar: true });
    botonDeLista('Message Catch: Solicitud de servicio').click();
    await fixture.whenStable();

    boton('Editar')!.click();
    await fixture.whenStable();
    expect(elemento.querySelector('.formulario__titulo')?.textContent?.trim()).toBe('Editar Message Catch');
    expect(elemento.querySelector<HTMLInputElement>('[formControlName="nombreMensaje"]')!.value).toBe('Solicitud de servicio');
    boton('Cancelar')!.click();
    await fixture.whenStable();

    boton('Eliminar')!.click();
    await fixture.whenStable();
    expect(previsualizarCatch).toHaveBeenCalledWith(5, 5);
    expect(elemento.querySelector('[role="alertdialog"] h2')?.textContent).toBe('¿Eliminar el Message Catch?');
    expect(elemento.querySelector('.eliminacion__consecuencias')?.textContent).toContain('quedará sin Message Catch homólogo');
    boton('Cancelar')!.click();
    await fixture.whenStable();
    expect(cambios).toBe(0);
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

  describe('movimiento de nodos en el lienzo', () => {
    const ESCRITURA: PermisosEdicion = { escribir: true, eliminar: true };

    async function arrastrar(clave: string, desde: Punto, ...recorrido: Punto[]): Promise<void> {
      const svg = elemento.querySelector('svg')!;
      const nodo = elemento.querySelector(`[data-nodo="${clave}"]`)!;
      const puntero = (tipo: string, destino: Element, punto: Punto) =>
        destino.dispatchEvent(
          new PointerEvent(tipo, { bubbles: true, button: 0, pointerId: 1, clientX: punto.x + 76, clientY: punto.y + 16 }),
        );
      puntero('pointerdown', nodo, desde);
      recorrido.forEach((punto) => puntero('pointermove', svg, punto));
      puntero('pointerup', svg, recorrido.at(-1) ?? desde);
      await fixture.whenStable();
    }

    function aviso(): string {
      return elemento.querySelector('.modelador__error')?.textContent?.trim() ?? '';
    }

    it('lleva una actividad a otra lane guardando lane y posición en una sola petición', async () => {
      await crear(diagramaCompleto(), ESCRITURA);
      moverActividad.mockReturnValue(of(actividad(2, 'Evaluar solicitud', 12, 360, 180)));

      await arrastrar('ACTIVIDAD:2', { x: 405, y: 83 }, { x: 425, y: 203 });

      expect(moverActividad).toHaveBeenCalledTimes(1);
      expect(moverActividad).toHaveBeenCalledWith(5, 2, { laneId: 12, posicionX: 360, posicionY: 180 });
      expect(cambios).toBe(1);
      expect(elemento.querySelector('.modelador__exito')?.textContent).toContain(
        'Se guardó la nueva posición de «Evaluar solicitud» en la lane «Supervisor».',
      );
      expect(elemento.querySelector('[data-nodo="ACTIVIDAD:2"]')?.getAttribute('transform')).toBe('translate(20 120)');
      expect(elemento.querySelector('[data-nodo="ACTIVIDAD:2"]')?.classList).toContain('nodo--seleccionado');

      const recargado = diagramaCompleto();
      recargado.actividades[1] = actividad(2, 'Evaluar solicitud', 12, 360, 180);
      fixture.componentRef.setInput('diagrama', recargado);
      await fixture.whenStable();

      expect(elemento.querySelector('[data-nodo="ACTIVIDAD:2"]')?.getAttribute('transform')).toBeNull();
      expect(elemento.querySelector('[data-nodo="ACTIVIDAD:2"] rect')?.getAttribute('y')).toBe('180');
      expect(elemento.textContent).not.toContain('está por encima de la lane');
    });

    it('devuelve el nodo a su posición y explica el rechazo del servidor', async () => {
      await crear(diagramaCompleto(), ESCRITURA);
      moverGateway.mockReturnValue(
        throwError(() => ({ tipo: 'sin-permiso', estado: 403, codigo: 'USUARIO_SIN_PERMISO', titulo: 'Sin permiso', detalle: 'Solo un administrador o editor puede crear o modificar gateways' })),
      );

      await arrastrar('GATEWAY:4', { x: 543, y: 93 }, { x: 583, y: 203 });

      expect(moverGateway).toHaveBeenCalledWith(5, 4, { posicionX: 560, posicionY: 180 });
      expect(elemento.querySelector('[data-nodo="GATEWAY:4"]')?.getAttribute('transform')).toBeNull();
      expect(aviso()).toBe(
        'No se pudo mover «Gateway EXCLUSIVO #4»: Solo un administrador o editor puede crear o modificar gateways. Se mantiene su posición anterior.',
      );
      expect(cambios).toBe(0);
    });

    it('rechaza sin llamar al servidor soltar en una caja negra o fuera del pool propio', async () => {
      await crear(diagramaCompleto(), ESCRITURA);

      await arrastrar('GATEWAY:4', { x: 543, y: 93 }, { x: 543, y: 540 });
      expect(aviso()).toBe('El pool «Servicio de correo» es una caja negra y no admite elementos.');

      await arrastrar('GATEWAY:4', { x: 543, y: 93 }, { x: 543, y: 390 });
      expect(aviso()).toBe('«Gateway EXCLUSIVO #4» conserva su pool al moverlo: suéltalo dentro de «Empresa de prueba».');

      await arrastrar('ACTIVIDAD:1', { x: 225, y: 83 }, { x: 225, y: 900 });
      expect(aviso()).toBe('Suelta «Registrar solicitud» dentro de un pool del diagrama.');
      expect(moverGateway).not.toHaveBeenCalled();
      expect(moverActividad).not.toHaveBeenCalled();
      expect(elemento.querySelector('[data-nodo="GATEWAY:4"]')?.getAttribute('transform')).toBeNull();
    });

    it('mueve un Message Catch dentro de su pool con su propio endpoint', async () => {
      await crear(diagramaCompleto(), ESCRITURA);
      moverCatch.mockReturnValue(of({ ...diagramaCompleto().messageCatches[0], posicionY: 180 }));

      await arrastrar('MESSAGE_CATCH:5', { x: 78, y: 88 }, { x: 78, y: 198 });

      expect(moverCatch).toHaveBeenCalledWith(5, 5, { posicionX: 60, posicionY: 180 });
      expect(cambios).toBe(1);
    });

    it('no mueve nodos sin permiso de escritura, con otra herramienta ni si se suelta en el mismo sitio', async () => {
      await crear(diagramaCompleto());
      await arrastrar('ACTIVIDAD:2', { x: 405, y: 83 }, { x: 425, y: 203 });
      expect(elemento.querySelector('.lienzo--movible')).toBeNull();

      TestBed.resetTestingModule();
      await crear(diagramaCompleto(), ESCRITURA);
      await pulsar(boton('Arco')!);
      await arrastrar('ACTIVIDAD:2', { x: 405, y: 83 }, { x: 425, y: 203 });
      await pulsar(boton('Seleccionar')!);
      await arrastrar('ACTIVIDAD:2', { x: 405, y: 83 }, { x: 407, y: 84 });
      await arrastrar('ACTIVIDAD:2', { x: 405, y: 83 }, { x: 425, y: 103 }, { x: 405, y: 83 });

      expect(moverActividad).not.toHaveBeenCalled();
      expect(cambios).toBe(0);
    });
  });
});

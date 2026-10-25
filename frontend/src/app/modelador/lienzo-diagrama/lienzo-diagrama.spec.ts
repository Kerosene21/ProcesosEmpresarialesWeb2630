import { ComponentFixture, TestBed } from '@angular/core/testing';

import { diagramaCompleto, diagramaInicial } from '../../../testing/diagramas';
import { DiagramaProceso } from '../../core/modelos/diagrama';
import { Seleccion } from '../../core/modelos/seleccion';
import { Punto, calcularGeometria } from '../geometria';
import { LienzoDiagrama, MovimientoNodo } from './lienzo-diagrama';

const SVG = 'http://www.w3.org/2000/svg';

describe('LienzoDiagrama', () => {
  let fixture: ComponentFixture<LienzoDiagrama>;
  let elemento: HTMLElement;
  let elegidos: Seleccion[];
  let puntos: Punto[];
  let movimientos: MovimientoNodo[];

  async function crear(diagrama: DiagramaProceso, seleccion: Seleccion | null = null): Promise<void> {
    fixture = TestBed.createComponent(LienzoDiagrama);
    elemento = fixture.nativeElement;
    elegidos = [];
    puntos = [];
    movimientos = [];
    fixture.componentInstance.elementoElegido.subscribe((elegido) => elegidos.push(elegido));
    fixture.componentInstance.fondoPulsado.subscribe((punto) => puntos.push(punto));
    fixture.componentInstance.nodoMovido.subscribe((movimiento) => movimientos.push(movimiento));
    fixture.componentRef.setInput('diagrama', diagrama);
    fixture.componentRef.setInput('geometria', calcularGeometria(diagrama));
    fixture.componentRef.setInput('seleccion', seleccion);
    await fixture.whenStable();
  }

  function textos(selector: string): string[] {
    return Array.from(elemento.querySelectorAll(selector)).map((nodo) => nodo.textContent?.trim() ?? '');
  }

  it('dibuja solo el pool y la lane iniciales cuando el proceso no tiene elementos', async () => {
    await crear(diagramaInicial());
    const svg = elemento.querySelector('svg')!;

    expect(svg.namespaceURI).toBe(SVG);
    expect(svg.getAttribute('viewBox')).toBe('-76 -16 972 172');
    expect(svg.getAttribute('width')).toBe('972');
    expect(textos('.pool__nombre')).toEqual(['Empresa de prueba']);
    expect(textos('.lane__nombre')).toEqual(['General']);
    expect(elemento.querySelectorAll('.nodo, .arco, .flujo').length).toBe(0);
  });

  it('representa pools, lanes, nodos, arcos y flujos de mensaje del DTO', async () => {
    await crear(diagramaCompleto());

    expect(textos('.pool__nombre')).toEqual(['Empresa de prueba', 'Cliente', 'Servicio de correo']);
    expect(textos('.lane__nombre')).toEqual(['General', 'Supervisor']);
    expect(elemento.querySelector('.pool--caja-negra')?.textContent).toContain('Caja negra');
    expect(elemento.querySelectorAll('g[appNodoActividad]').length).toBe(3);
    expect(elemento.querySelectorAll('g[appNodoGateway]').length).toBe(1);
    expect(elemento.querySelectorAll('g[appNodoEvento]').length).toBe(3);
    expect(elemento.querySelectorAll('g[appArcoSecuencia]').length).toBe(6);
    expect(elemento.querySelectorAll('g[appFlujoMensaje] .flujo__linea').length).toBe(2);
  });

  it('distingue el tipo de cada pool y describe al participante representado', async () => {
    const diagrama = diagramaCompleto();
    diagrama.pools[1] = { ...diagrama.pools[1], tipo: 'PARTICIPANTE', empresaId: 9, empresaNombre: 'Andes SAS' };
    await crear(diagrama);
    const tipos = Array.from(elemento.querySelectorAll('g.pool')).map((grupo) => grupo.getAttribute('data-tipo'));

    expect(tipos).toEqual(['PROPIETARIO', 'PARTICIPANTE', 'EXTERNO']);
    expect(elemento.querySelector('[data-pool="81"]')?.classList).toContain('pool--participante');
    expect(elemento.querySelector('[data-pool="82"]')?.classList).toContain('pool--externo');
    expect(textos('g.pool > title')).toEqual([
      'Pool propietario «Empresa de prueba» · Empresa de prueba',
      'Pool participante «Cliente» · Andes SAS',
      'Pool externo «Servicio de correo» · caja negra',
    ]);
    expect(elemento.querySelector('[data-pool="82"] .pool__nota')?.textContent?.trim()).toBe('Caja negra · Externo');
    expect(elemento.querySelectorAll('[data-pool="82"] .lane').length).toBe(0);
  });

  it('coloca pools y lanes con la geometría calculada y gira sus nombres', async () => {
    await crear(diagramaCompleto());
    const lane = elemento.querySelector('[data-lane="12"] .lane__marco')!;
    const nombre = elemento.querySelector('[data-pool="81"] .pool__nombre')!;

    expect(['x', 'y', 'width', 'height'].map((atributo) => lane.getAttribute(atributo))).toEqual(['-28', '140', '908', '140']);
    expect(nombre.getAttribute('transform')).toBe('rotate(-90 -44 390)');
  });

  it('define flechas propias y las referencia desde los arcos', async () => {
    await crear(diagramaCompleto());
    const ids = Array.from(elemento.querySelectorAll('marker')).map((marcador) => marcador.id);
    const referencia = elemento.querySelector('.arco__linea')?.getAttribute('marker-end');

    expect(ids).toHaveLength(3);
    expect(new Set(ids).size).toBe(3);
    expect(referencia).toBe(`url(#${ids[0]})`);
    expect(elemento.querySelector('.flujo__linea')?.getAttribute('marker-end')).toBe(`url(#${ids[2]})`);
  });

  it('resalta únicamente el elemento seleccionado', async () => {
    await crear(diagramaCompleto(), { tipo: 'GATEWAY', id: 4 });

    expect(elemento.querySelectorAll('.nodo--seleccionado').length).toBe(1);
    expect(elemento.querySelector('g[appNodoGateway]')?.classList).toContain('nodo--seleccionado');

    fixture.componentRef.setInput('seleccion', { tipo: 'ARCO', id: 33 });
    await fixture.whenStable();

    expect(elemento.querySelectorAll('.nodo--seleccionado').length).toBe(0);
    expect(elemento.querySelectorAll('.arco--seleccionado').length).toBe(1);
  });

  it('informa del elemento elegido y del punto absoluto pulsado en el fondo', async () => {
    await crear(diagramaCompleto());

    elemento.querySelectorAll<SVGGElement>('g[appNodoEvento]')[2].dispatchEvent(new MouseEvent('click', { bubbles: true }));
    elemento.querySelector('svg')!.dispatchEvent(new MouseEvent('click', { bubbles: true, clientX: 300, clientY: 216 }));

    expect(elegidos).toEqual([{ tipo: 'ENVIO_EXTERNO', id: 7 }]);
    expect(puntos).toEqual([{ x: 224, y: 200 }]);
  });

  it('no dibuja arcos sin coordenadas', async () => {
    const diagrama = diagramaCompleto();
    diagrama.arcos[0] = { ...diagrama.arcos[0], origenX: null };

    await crear(diagrama);

    expect(elemento.querySelectorAll('g[appArcoSecuencia]').length).toBe(5);
  });

  describe('arrastre de nodos', () => {
    function puntero(tipo: string, destino: Element, clientX: number, clientY: number): void {
      destino.dispatchEvent(new PointerEvent(tipo, { bubbles: true, button: 0, pointerId: 1, clientX, clientY }));
    }

    function lineaDelArco(indice: number): string[] {
      const linea = elemento.querySelectorAll('g[appArcoSecuencia]')[indice].querySelector('.arco__linea')!;
      return ['x1', 'y1', 'x2', 'y2'].map((atributo) => linea.getAttribute(atributo) ?? '');
    }

    async function crearMovible(): Promise<SVGSVGElement> {
      await crear(diagramaCompleto());
      fixture.componentRef.setInput('movible', true);
      await fixture.whenStable();
      return elemento.querySelector('svg')!;
    }

    it('desplaza la actividad y sus arcos mientras se arrastra y emite un único movimiento al soltar', async () => {
      const svg = await crearMovible();
      const evaluar = elemento.querySelector('[data-nodo="ACTIVIDAD:2"]')!;

      puntero('pointerdown', evaluar.querySelector('rect')!, 481, 99);
      puntero('pointermove', svg, 483, 100);
      puntero('pointermove', svg, 501, 219);
      await fixture.whenStable();

      expect(evaluar.getAttribute('transform')).toBe('translate(20 120)');
      expect(lineaDelArco(1)).toEqual(['225', '83', '387', '180']);
      expect(elemento.querySelector('.lienzo')?.classList).toContain('lienzo--arrastrando');
      expect(movimientos).toEqual([]);

      puntero('pointerup', svg, 501, 219);
      svg.dispatchEvent(new MouseEvent('click', { bubbles: true, clientX: 501, clientY: 219 }));
      await fixture.whenStable();

      expect(movimientos).toEqual([{ elemento: { tipo: 'ACTIVIDAD', id: 2 }, esquina: { x: 360, y: 180 } }]);
      expect(evaluar.getAttribute('transform')).toBeNull();
      expect(lineaDelArco(1)).toEqual(['225', '83', '340', '83']);
      expect(puntos).toEqual([]);
      expect(elegidos).toEqual([]);
    });

    it('distingue un clic de selección de un arrastre', async () => {
      const svg = await crearMovible();
      const gateway = elemento.querySelector('[data-nodo="GATEWAY:4"]')!;

      puntero('pointerdown', gateway, 619, 109);
      puntero('pointermove', svg, 621, 110);
      puntero('pointerup', svg, 621, 110);
      gateway.dispatchEvent(new MouseEvent('click', { bubbles: true }));
      await fixture.whenStable();

      expect(movimientos).toEqual([]);
      expect(elegidos).toEqual([{ tipo: 'GATEWAY', id: 4 }]);
      expect(gateway.getAttribute('transform')).toBeNull();
    });

    it('no arrastra si el lienzo no admite movimientos y cancela el arrastre interrumpido', async () => {
      await crear(diagramaCompleto());
      const svg = elemento.querySelector('svg')!;
      const evaluar = elemento.querySelector('[data-nodo="ACTIVIDAD:2"]')!;

      puntero('pointerdown', evaluar, 481, 99);
      puntero('pointermove', svg, 501, 219);
      puntero('pointerup', svg, 501, 219);
      await fixture.whenStable();
      expect(evaluar.getAttribute('transform')).toBeNull();

      fixture.componentRef.setInput('movible', true);
      await fixture.whenStable();
      puntero('pointerdown', evaluar, 481, 99);
      puntero('pointermove', svg, 501, 219);
      svg.dispatchEvent(new PointerEvent('pointercancel', { bubbles: true, pointerId: 1 }));
      puntero('pointerup', svg, 501, 219);
      await fixture.whenStable();

      expect(movimientos).toEqual([]);
      expect(evaluar.getAttribute('transform')).toBeNull();
    });

    it('mantiene el nodo en su posición provisional mientras el servidor confirma el movimiento', async () => {
      await crear(diagramaCompleto());

      fixture.componentRef.setInput('provisional', { elemento: { tipo: 'GATEWAY', id: 4 }, esquina: { x: 560, y: 180 } });
      await fixture.whenStable();

      expect(elemento.querySelector('[data-nodo="GATEWAY:4"]')?.getAttribute('transform')).toBe('translate(40 110)');
      expect(lineaDelArco(2)[2]).not.toBe('520');

      fixture.componentRef.setInput('provisional', null);
      await fixture.whenStable();
      expect(elemento.querySelector('[data-nodo="GATEWAY:4"]')?.getAttribute('transform')).toBeNull();
      expect(lineaDelArco(2)).toEqual(['405', '83', '520', '91']);
    });
  });
});

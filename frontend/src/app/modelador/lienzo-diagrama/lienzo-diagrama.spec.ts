import { ComponentFixture, TestBed } from '@angular/core/testing';

import { diagramaCompleto, diagramaInicial } from '../../../testing/diagramas';
import { DiagramaProceso } from '../../core/modelos/diagrama';
import { Seleccion } from '../../core/modelos/seleccion';
import { Punto, calcularGeometria } from '../geometria';
import { LienzoDiagrama } from './lienzo-diagrama';

const SVG = 'http://www.w3.org/2000/svg';

describe('LienzoDiagrama', () => {
  let fixture: ComponentFixture<LienzoDiagrama>;
  let elemento: HTMLElement;
  let elegidos: Seleccion[];
  let puntos: Punto[];

  async function crear(diagrama: DiagramaProceso, seleccion: Seleccion | null = null): Promise<void> {
    fixture = TestBed.createComponent(LienzoDiagrama);
    elemento = fixture.nativeElement;
    elegidos = [];
    puntos = [];
    fixture.componentInstance.elementoElegido.subscribe((elegido) => elegidos.push(elegido));
    fixture.componentInstance.fondoPulsado.subscribe((punto) => puntos.push(punto));
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
});

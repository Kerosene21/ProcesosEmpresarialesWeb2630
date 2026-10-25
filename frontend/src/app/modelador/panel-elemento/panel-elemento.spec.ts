import { ComponentFixture, TestBed } from '@angular/core/testing';

import { diagramaCompleto } from '../../../testing/diagramas';
import { Seleccion } from '../../core/modelos/seleccion';
import { PanelElemento, describirElemento } from './panel-elemento';

describe('PanelElemento', () => {
  let fixture: ComponentFixture<PanelElemento>;
  let elemento: HTMLElement;
  let limpiezas: number;

  async function crear(seleccion: Seleccion | null): Promise<void> {
    fixture = TestBed.createComponent(PanelElemento);
    elemento = fixture.nativeElement;
    limpiezas = 0;
    fixture.componentInstance.limpiar.subscribe(() => limpiezas++);
    fixture.componentRef.setInput('diagrama', diagramaCompleto());
    fixture.componentRef.setInput('seleccion', seleccion);
    await fixture.whenStable();
  }

  function dato(etiqueta: string): string | undefined {
    return Array.from(elemento.querySelectorAll('.panel__datos div'))
      .find((fila) => fila.querySelector('dt')?.textContent === etiqueta)
      ?.querySelector('dd')?.textContent ?? undefined;
  }

  it('invita a seleccionar cuando no hay elemento elegido', async () => {
    await crear(null);

    expect(elemento.textContent).toContain('Selecciona un elemento');
    expect(elemento.querySelector('dl')).toBeNull();
  });

  it('muestra la información real de la actividad seleccionada', async () => {
    await crear({ tipo: 'ACTIVIDAD', id: 3 });

    expect(elemento.querySelector('.panel__nombre')?.textContent).toBe('Aprobar solicitud');
    expect(dato('Lane')).toBe('Supervisor');
    expect(dato('Pool')).toBe('Empresa de prueba');
    expect(dato('Posición')).toBe('x 620, y 200');
    expect(dato('Conexiones')).toBe('1 de entrada, 1 de salida');
  });

  it('detalla las salidas condicionadas de un gateway', async () => {
    await crear({ tipo: 'GATEWAY', id: 4 });

    expect(dato('Tipo')).toBe('Exclusivo (X)');
    expect(dato('Salida a Aprobar solicitud')).toBe('Aprobada');
    expect(dato('Salida a Envío externo: Notificación de resultado')).toBe('Rechazada');
  });

  it('describe arcos y eventos con sus datos de origen, destino y correlación', () => {
    const diagrama = diagramaCompleto();

    expect(describirElemento(diagrama, { tipo: 'ARCO', id: 32 })?.filas).toEqual([
      { etiqueta: 'Origen', valor: 'Registrar solicitud' },
      { etiqueta: 'Destino', valor: 'Evaluar solicitud' },
      { etiqueta: 'Etiqueta', valor: 'Sin definir' },
      { etiqueta: 'Condición', valor: 'Sin condición' },
    ]);
    expect(describirElemento(diagrama, { tipo: 'MESSAGE_CATCH', id: 5 })?.filas[0]).toEqual({ etiqueta: 'Variante', valor: 'De inicio' });
    expect(describirElemento(diagrama, { tipo: 'MESSAGE_THROW', id: 6 })?.filas[5]).toEqual({ etiqueta: 'Catch homólogo', valor: '#5' });
    expect(describirElemento(diagrama, { tipo: 'ENVIO_EXTERNO', id: 7 })?.filas[2]).toEqual({ etiqueta: 'Medio', valor: 'Correo' });
    expect(describirElemento(diagrama, { tipo: 'ACTIVIDAD', id: 99 })).toBeNull();
  });

  it('lista las advertencias del elemento y permite limpiar la selección', async () => {
    await crear({ tipo: 'ARCO', id: 31 });
    const diagrama = diagramaCompleto();
    diagrama.arcos[0] = { ...diagrama.arcos[0], advertencias: ['El gateway queda sin salidas'] };
    fixture.componentRef.setInput('diagrama', diagrama);
    await fixture.whenStable();

    expect(elemento.querySelector('.panel__advertencias')?.textContent).toContain('El gateway queda sin salidas');
    Array.from(elemento.querySelectorAll('button'))
      .find((boton) => boton.textContent?.trim() === 'Limpiar selección')!
      .click();

    expect(limpiezas).toBe(1);
  });
});

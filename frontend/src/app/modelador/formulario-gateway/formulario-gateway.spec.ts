import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { diagramaCompleto, gateway } from '../../../testing/diagramas';
import { GatewayRespuesta } from '../../core/modelos/diagrama';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { CrearGateway, EditarGateway } from '../../core/modelos/gateway';
import { GatewayService } from '../../core/servicios/gateway-service';
import { calcularGeometria } from '../geometria';
import { FormularioGateway, PropuestaGateway } from './formulario-gateway';

function fallo(tipo: FalloHttp['tipo'], estado: number, codigo: string, detalle: string): FalloHttp {
  return { tipo, estado, codigo, titulo: 'Rechazado', detalle };
}

describe('FormularioGateway', () => {
  let fixture: ComponentFixture<FormularioGateway>;
  let elemento: HTMLElement;
  let guardados: GatewayRespuesta[];
  const crearApi = vi.fn<(procesoId: number, datos: CrearGateway) => Observable<GatewayRespuesta>>();
  const editarApi = vi.fn<(procesoId: number, id: number, datos: EditarGateway) => Observable<GatewayRespuesta>>();

  async function crear(actual: GatewayRespuesta | null, propuesta: PropuestaGateway | null): Promise<void> {
    crearApi.mockReset();
    editarApi.mockReset();
    TestBed.configureTestingModule({
      providers: [{ provide: GatewayService, useValue: { crear: crearApi, editar: editarApi } }],
    });
    fixture = TestBed.createComponent(FormularioGateway);
    elemento = fixture.nativeElement;
    guardados = [];
    fixture.componentInstance.guardado.subscribe((guardado) => guardados.push(guardado));
    const diagrama = diagramaCompleto();
    fixture.componentRef.setInput('procesoId', 5);
    fixture.componentRef.setInput('diagrama', diagrama);
    fixture.componentRef.setInput('geometria', calcularGeometria(diagrama));
    fixture.componentRef.setInput('gateway', actual);
    fixture.componentRef.setInput('propuesta', propuesta);
    await fixture.whenStable();
  }

  function control<T extends HTMLElement>(nombre: string): T {
    return elemento.querySelector<T>(`[formControlName="${nombre}"]`)!;
  }

  function porEtiqueta(texto: string): HTMLInputElement {
    const etiqueta = Array.from(elemento.querySelectorAll('label')).find((actual) => actual.textContent?.trim() === texto)!;
    return elemento.querySelector<HTMLInputElement>(`[id="${etiqueta.htmlFor}"]`)!;
  }

  async function escribir(campo: HTMLInputElement, valor: string): Promise<void> {
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function elegir(nombre: string, texto: string): Promise<void> {
    const lista = control<HTMLSelectElement>(nombre);
    lista.selectedIndex = Array.from(lista.options).findIndex((opcion) => opcion.textContent?.trim() === texto);
    lista.dispatchEvent(new Event('change'));
    await fixture.whenStable();
  }

  async function enviar(): Promise<void> {
    elemento.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('crea el gateway con el tipo elegido en el pool y la posición propuestos', async () => {
    const creado = gateway(9, 136, 367, { tipo: 'PARALELO', poolId: 81 });
    await crear(null, { poolId: 81, posicion: { x: 136, y: 367 } });
    crearApi.mockReturnValue(of(creado));

    expect(Array.from(control<HTMLSelectElement>('poolId').options).map((opcion) => opcion.textContent?.trim())).toEqual([
      'Elige un pool',
      'Empresa de prueba',
      'Cliente',
    ]);
    await elegir('tipo', 'Paralelo');
    await enviar();

    expect(crearApi).toHaveBeenCalledWith(5, { tipo: 'PARALELO', posicionX: 136, posicionY: 367, poolId: 81 });
    expect(guardados).toEqual([creado]);
  });

  it('mantiene la posición dentro del pool elegido y rechaza la caja negra que informe el servidor', async () => {
    await crear(null, { poolId: 81, posicion: { x: 136, y: 367 } });
    await elegir('poolId', 'Empresa de prueba');

    expect(control<HTMLInputElement>('posicionY').value).toBe('214');

    await elegir('tipo', 'Exclusivo');
    crearApi.mockReturnValue(throwError(() => fallo('conflicto', 409, 'POOL_CAJA_NEGRA', 'El pool es una caja negra')));
    await enviar();

    expect(control('poolId').getAttribute('aria-invalid')).toBe('true');
    expect(elemento.textContent).toContain('El pool es una caja negra');
  });

  it('edita el tipo y las condiciones de cada arco saliente', async () => {
    const original = diagramaCompleto().gateways[0];
    await crear(original, null);
    editarApi.mockReturnValue(of({ ...original, tipo: 'INCLUSIVO' }));

    expect(porEtiqueta('Hacia Aprobar solicitud').value).toBe('Aprobada');
    expect(porEtiqueta('Hacia Envío externo: Notificación de resultado').value).toBe('Rechazada');
    expect(control('poolId')).toBeNull();
    await elegir('tipo', 'Inclusivo');
    await escribir(porEtiqueta('Hacia Aprobar solicitud'), ' Monto alto ');
    await enviar();

    expect(editarApi).toHaveBeenCalledWith(5, 4, { tipo: 'INCLUSIVO', condiciones: { 34: 'Monto alto', 35: 'Rechazada' } });
    expect(guardados).toHaveLength(1);
  });

  it('exige condición en cada salida de un gateway exclusivo', async () => {
    await crear(diagramaCompleto().gateways[0], null);
    await escribir(porEtiqueta('Hacia Aprobar solicitud'), '');
    await enviar();

    expect(editarApi).not.toHaveBeenCalled();
    expect(elemento.textContent).toContain('Cada salida de un gateway exclusivo o inclusivo necesita condición');
  });

  it('al pasar a paralelo deshabilita las condiciones y no las envía', async () => {
    const original = diagramaCompleto().gateways[0];
    await crear(original, null);
    editarApi.mockReturnValue(of({ ...original, tipo: 'PARALELO' }));
    await elegir('tipo', 'Paralelo');

    expect(porEtiqueta('Hacia Aprobar solicitud').disabled).toBe(true);
    expect(elemento.textContent).toContain('Un gateway paralelo no lleva condiciones');
    await enviar();

    expect(editarApi).toHaveBeenCalledWith(5, 4, { tipo: 'PARALELO', condiciones: {} });
  });

  it('muestra como aviso la regla BPMN que rechaza el servidor', async () => {
    await crear(diagramaCompleto().gateways[0], null);
    editarApi.mockReturnValue(
      throwError(() =>
        fallo('datos-no-validos', 400, 'CONDICION_NO_VALIDA', 'Cada arco de salida de un gateway exclusivo o inclusivo necesita condición'),
      ),
    );
    await enviar();

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('Cada arco de salida de un gateway');
    expect(guardados).toEqual([]);
  });
});

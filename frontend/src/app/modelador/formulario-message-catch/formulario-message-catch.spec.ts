import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { arco, diagramaCompleto } from '../../../testing/diagramas';
import { DiagramaProceso, MessageCatchRespuesta } from '../../core/modelos/diagrama';
import { CrearMessageCatch, EditarMessageCatch } from '../../core/modelos/evento';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { MessageCatchService } from '../../core/servicios/message-catch-service';
import { PropuestaEvento } from '../eventos';
import { calcularGeometria } from '../geometria';
import { FormularioMessageCatch } from './formulario-message-catch';

function fallo(tipo: FalloHttp['tipo'], estado: number, codigo: string, detalle: string): FalloHttp {
  return { tipo, estado, codigo, titulo: 'Rechazado', detalle };
}

describe('FormularioMessageCatch', () => {
  let fixture: ComponentFixture<FormularioMessageCatch>;
  let elemento: HTMLElement;
  let guardados: MessageCatchRespuesta[];
  const crearApi = vi.fn<(procesoId: number, datos: CrearMessageCatch) => Observable<MessageCatchRespuesta>>();
  const editarApi = vi.fn<(procesoId: number, id: number, datos: EditarMessageCatch) => Observable<MessageCatchRespuesta>>();

  async function crear(
    actual: MessageCatchRespuesta | null,
    propuesta: PropuestaEvento | null,
    diagrama: DiagramaProceso = diagramaCompleto(),
  ): Promise<void> {
    crearApi.mockReset();
    editarApi.mockReset();
    TestBed.configureTestingModule({
      providers: [{ provide: MessageCatchService, useValue: { crear: crearApi, editar: editarApi } }],
    });
    fixture = TestBed.createComponent(FormularioMessageCatch);
    elemento = fixture.nativeElement;
    guardados = [];
    fixture.componentInstance.guardado.subscribe((guardado) => guardados.push(guardado));
    fixture.componentRef.setInput('procesoId', 5);
    fixture.componentRef.setInput('diagrama', diagrama);
    fixture.componentRef.setInput('geometria', calcularGeometria(diagrama));
    fixture.componentRef.setInput('messageCatch', actual);
    fixture.componentRef.setInput('propuesta', propuesta);
    await fixture.whenStable();
  }

  function control<T extends HTMLElement>(nombre: string): T {
    return elemento.querySelector<T>(`[formControlName="${nombre}"]`)!;
  }

  function errores(): string[] {
    return Array.from(elemento.querySelectorAll('.campo__error'))
      .map((error) => error.textContent?.trim() ?? '')
      .filter((texto) => texto.length > 0);
  }

  async function escribir(nombre: string, valor: string): Promise<void> {
    const campo = control<HTMLInputElement>(nombre);
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

  async function marcarOrigenExterno(): Promise<void> {
    control<HTMLInputElement>('origenExterno').click();
    await fixture.whenStable();
  }

  async function enviar(): Promise<void> {
    elemento.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  async function completar(): Promise<void> {
    await escribir('nombreMensaje', 'Pago recibido');
    await escribir('datosEsperados', 'numeroFactura, valorPagado');
    await escribir('actividadesUso', 'Registrar pago');
  }

  it('crea un Message Catch de inicio con origen externo que siempre inicia un caso nuevo', async () => {
    const existente = diagramaCompleto().messageCatches[0];
    await crear(null, { poolId: 80, posicion: { x: 60, y: 200 } });
    crearApi.mockReturnValue(of({ ...existente, id: 10 }));

    await completar();
    await elegir('variante', 'De inicio');
    expect(control<HTMLSelectElement>('comportamientoSinCaso').disabled).toBe(true);
    expect(elemento.textContent).toContain('no admite flujos de secuencia entrantes');
    await marcarOrigenExterno();
    await enviar();

    expect(crearApi).toHaveBeenCalledWith(5, {
      nombreMensaje: 'Pago recibido',
      variante: 'INICIO',
      datosEsperados: 'numeroFactura, valorPagado',
      actividadesUso: 'Registrar pago',
      origenExterno: true,
      claveCorrelacion: null,
      comportamientoSinCaso: 'INICIAR_NUEVO_CASO',
      poolId: 80,
      posicionX: 60,
      posicionY: 200,
    });
    expect(guardados.map((guardado) => guardado.id)).toEqual([10]);
  });

  it('crea un Message Catch intermedio con su correlación documentada', async () => {
    await crear(null, { poolId: 80, posicion: { x: 300, y: 200 } });
    crearApi.mockReturnValue(of(diagramaCompleto().messageCatches[0]));

    await completar();
    await elegir('variante', 'Intermedio');
    await escribir('claveCorrelacion', 'numeroFactura');
    await elegir('comportamientoSinCaso', 'Descartar el mensaje');
    await enviar();

    expect(crearApi).toHaveBeenCalledWith(
      5,
      expect.objectContaining({
        variante: 'INTERMEDIO',
        origenExterno: false,
        claveCorrelacion: 'numeroFactura',
        comportamientoSinCaso: 'DESCARTAR',
      }),
    );
  });

  it('exige el nombre, la variante, los datos esperados y las actividades que los usan', async () => {
    await crear(null, { poolId: 80, posicion: { x: 300, y: 200 } });

    await enviar();

    expect(errores()).toEqual([
      'El nombre del mensaje esperado es obligatorio',
      'Indica si el evento es de inicio o intermedio',
      'Documenta los datos que se esperan',
      'Indica qué actividades usan los datos recibidos',
    ]);
    expect(crearApi).not.toHaveBeenCalled();
  });

  it('advierte que un catch con entradas no puede pasar a inicio y muestra el rechazo del servidor', async () => {
    const diagrama = diagramaCompleto();
    diagrama.messageCatches[0] = { ...diagrama.messageCatches[0], variante: 'INTERMEDIO' };
    diagrama.arcos.push(arco(40, ['ACTIVIDAD', 2, 'Evaluar solicitud'], ['EVENTO', 5, 'Message Catch: Solicitud de servicio'], [405, 83, 78, 88]));
    await crear(diagrama.messageCatches[0], null, diagrama);
    editarApi.mockReturnValue(
      throwError(() =>
        fallo('datos-no-validos', 400, 'CATCH_INICIO_CON_ENTRADA', "El Message Catch 'Solicitud de servicio' tiene 1 flujos de secuencia entrantes"),
      ),
    );

    expect(control<HTMLSelectElement>('poolId')).toBeNull();
    await elegir('variante', 'De inicio');
    expect(elemento.querySelector('.formulario__alerta')?.textContent).toContain('Tiene 1 flujo de secuencia entrante');
    await enviar();

    expect(editarApi).toHaveBeenCalledWith(5, 5, expect.objectContaining({ variante: 'INICIO', comportamientoSinCaso: 'INICIAR_NUEVO_CASO' }));
    expect(errores()).toEqual(["El Message Catch 'Solicitud de servicio' tiene 1 flujos de secuencia entrantes"]);
    expect(guardados).toEqual([]);
  });

  it('muestra junto al comportamiento el rechazo de una correlación no válida', async () => {
    const intermedio: MessageCatchRespuesta = { ...diagramaCompleto().messageCatches[0], variante: 'INTERMEDIO', comportamientoSinCaso: 'DESCARTAR' };
    await crear(intermedio, null);
    editarApi.mockReturnValue(
      throwError(() => fallo('datos-no-validos', 400, 'CORRELACION_NO_VALIDA', 'Un Message Catch de inicio siempre inicia un caso nuevo')),
    );

    await enviar();

    expect(errores()).toEqual(['Un Message Catch de inicio siempre inicia un caso nuevo']);
  });

  it('muestra las advertencias de correlación actuales del evento en edición', async () => {
    const actual: MessageCatchRespuesta = {
      ...diagramaCompleto().messageCatches[0],
      variante: 'INTERMEDIO',
      advertencias: ["El Message Catch intermedio 'Solicitud de servicio' no declara clave de correlación"],
    };
    await crear(actual, null);

    expect(elemento.querySelector('.formulario__advertencias')?.textContent).toContain('no declara clave de correlación');
    expect(control<HTMLInputElement>('origenExterno').checked).toBe(false);
  });
});

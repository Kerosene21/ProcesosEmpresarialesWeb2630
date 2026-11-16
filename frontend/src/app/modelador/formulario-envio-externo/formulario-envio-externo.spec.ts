import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { diagramaCompleto } from '../../../testing/diagramas';
import { DiagramaProceso, EnvioExternoRespuesta } from '../../core/modelos/diagrama';
import { CrearEnvioExterno, EditarEnvioExterno } from '../../core/modelos/evento';
import { FalloHttp } from '../../core/modelos/fallo-http';
import { EnvioExternoService } from '../../core/servicios/envio-externo-service';
import { PropuestaEvento } from '../eventos';
import { calcularGeometria } from '../geometria';
import { FormularioEnvioExterno } from './formulario-envio-externo';

function fallo(tipo: FalloHttp['tipo'], estado: number, codigo: string, detalle: string): FalloHttp {
  return { tipo, estado, codigo, titulo: 'Rechazado', detalle };
}

describe('FormularioEnvioExterno', () => {
  let fixture: ComponentFixture<FormularioEnvioExterno>;
  let elemento: HTMLElement;
  let guardados: EnvioExternoRespuesta[];
  const crearApi = vi.fn<(procesoId: number, datos: CrearEnvioExterno) => Observable<EnvioExternoRespuesta>>();
  const editarApi = vi.fn<(procesoId: number, id: number, datos: EditarEnvioExterno) => Observable<EnvioExternoRespuesta>>();

  async function crear(
    actual: EnvioExternoRespuesta | null,
    propuesta: PropuestaEvento | null,
    diagrama: DiagramaProceso = diagramaCompleto(),
  ): Promise<void> {
    crearApi.mockReset();
    editarApi.mockReset();
    TestBed.configureTestingModule({
      providers: [{ provide: EnvioExternoService, useValue: { crear: crearApi, editar: editarApi } }],
    });
    fixture = TestBed.createComponent(FormularioEnvioExterno);
    elemento = fixture.nativeElement;
    guardados = [];
    fixture.componentInstance.guardado.subscribe((guardado) => guardados.push(guardado));
    fixture.componentRef.setInput('procesoId', 5);
    fixture.componentRef.setInput('diagrama', diagrama);
    fixture.componentRef.setInput('geometria', calcularGeometria(diagrama));
    fixture.componentRef.setInput('envio', actual);
    fixture.componentRef.setInput('propuesta', propuesta);
    await fixture.whenStable();
  }

  function control<T extends HTMLElement>(nombre: string): T {
    return elemento.querySelector<T>(`[formControlName="${nombre}"]`)!;
  }

  function opciones(nombre: string): string[] {
    return Array.from(control<HTMLSelectElement>(nombre).options).map((opcion) => opcion.textContent?.trim() ?? '');
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

  async function enviar(): Promise<void> {
    elemento.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  it('documenta el envío hacia el único sistema externo de caja negra', async () => {
    await crear(null, { poolId: 80, posicion: { x: 820, y: 200 } });
    crearApi.mockReturnValue(of({ ...diagramaCompleto().enviosExternos[0], id: 11 }));

    expect(opciones('poolDestinoId')).toEqual(['Elige el sistema externo', 'Servicio de correo (externo, caja negra)']);
    expect(control<HTMLSelectElement>('poolDestinoId').selectedIndex).toBe(1);
    expect(elemento.textContent).toContain('no se registran credenciales');
    await escribir('nombreMensaje', 'NotificarDespacho');
    await elegir('tipoDestino', 'Servicio web');
    await escribir('datosEnviados', 'numeroGuia, fechaDespacho');
    await escribir('momentoProceso', 'Al confirmar el despacho');
    await elegir('comportamientoFallo', 'Seguir la ruta de error');
    await enviar();

    expect(crearApi).toHaveBeenCalledWith(5, {
      nombreMensaje: 'NotificarDespacho',
      poolDestinoId: 82,
      tipoDestino: 'SERVICIO_WEB',
      datosEnviados: 'numeroGuia, fechaDespacho',
      momentoProceso: 'Al confirmar el despacho',
      comportamientoFallo: 'RUTA_ERROR',
      claveCorrelacion: null,
      poolOrigenId: 80,
      posicionX: 820,
      posicionY: 200,
    });
    expect(guardados.map((guardado) => guardado.id)).toEqual([11]);
  });

  it('sin un pool externo de caja negra explica cómo crearlo y no permite guardar', async () => {
    const diagrama = diagramaCompleto();
    diagrama.pools = diagrama.pools.map((pool) => ({ ...pool, cajaNegra: false }));
    await crear(null, { poolId: 80, posicion: { x: 820, y: 200 } }, diagrama);

    expect(opciones('poolDestinoId')).toEqual(['Elige el sistema externo']);
    expect(elemento.textContent).toContain('No hay sistemas externos');
    expect(elemento.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(true);
  });

  it('exige el medio, los datos, el momento y el comportamiento ante fallo', async () => {
    await crear(null, { poolId: 80, posicion: { x: 820, y: 200 } });

    await enviar();

    expect(errores()).toEqual([
      'El nombre del envío es obligatorio',
      'Indica el medio del envío',
      'Documenta los datos que se envían',
      'Indica en qué momento del proceso ocurre',
      'Indica qué hace el proceso si el envío falla',
    ]);
    expect(crearApi).not.toHaveBeenCalled();
  });

  it('edita la documentación sin cambiar el pool emisor ni la posición', async () => {
    const actual = diagramaCompleto().enviosExternos[0];
    await crear(actual, null);
    editarApi.mockReturnValue(of(actual));

    expect(control<HTMLSelectElement>('poolOrigenId')).toBeNull();
    expect(elemento.textContent).toContain('Sale del pool «Empresa de prueba» en x 820, y 70');
    await elegir('tipoDestino', 'Cola de mensajes');
    await elegir('comportamientoFallo', 'Finalizar');
    await enviar();

    expect(editarApi).toHaveBeenCalledWith(5, 7, {
      nombreMensaje: 'Notificación de resultado',
      poolDestinoId: 82,
      tipoDestino: 'COLA',
      datosEnviados: 'Número de solicitud y resultado',
      momentoProceso: 'Al cerrar la solicitud',
      comportamientoFallo: 'FINALIZAR',
      claveCorrelacion: 'numeroSolicitud',
    });
  });

  it('muestra junto al sistema externo el rechazo de un destino no válido', async () => {
    await crear(diagramaCompleto().enviosExternos[0], null);
    editarApi.mockReturnValue(
      throwError(() =>
        fallo('datos-no-validos', 400, 'ENVIO_EXTERNO_NO_VALIDO', "El pool externo 'Servicio de correo' no es una caja negra"),
      ),
    );

    await enviar();

    expect(errores()).toEqual(["El pool externo 'Servicio de correo' no es una caja negra"]);
    expect(guardados).toEqual([]);
  });
});

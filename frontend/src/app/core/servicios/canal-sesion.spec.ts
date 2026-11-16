import { DOCUMENT } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import {
  ALMACEN_MARCAS,
  CANAL_SESION,
  NOMBRE_CANAL_SESION,
  esMensajeDeCierre,
} from './canal-sesion';

function documentoCon(ventana: object): Document {
  return { defaultView: ventana } as unknown as Document;
}

describe('canal de sesión entre pestañas', () => {
  it('usa un BroadcastChannel con nombre propio cuando el navegador lo ofrece', () => {
    const nombres: string[] = [];
    class CanalNavegador {
      constructor(nombre: string) {
        nombres.push(nombre);
      }
      postMessage(): void {
        return;
      }
      addEventListener(): void {
        return;
      }
    }
    TestBed.configureTestingModule({
      providers: [
        { provide: DOCUMENT, useValue: documentoCon({ BroadcastChannel: CanalNavegador }) },
      ],
    });

    expect(TestBed.inject(CANAL_SESION)).toBeInstanceOf(CanalNavegador);
    expect(nombres).toEqual([NOMBRE_CANAL_SESION]);
  });

  it('sin BroadcastChannel la aplicación funciona sin sincronizar pestañas', () => {
    TestBed.configureTestingModule({
      providers: [{ provide: DOCUMENT, useValue: documentoCon({}) }],
    });

    expect(TestBed.inject(CANAL_SESION)).toBeNull();
  });

  it('si el navegador bloquea localStorage no hay almacén de marcas', () => {
    const ventana = {};
    Object.defineProperty(ventana, 'localStorage', {
      get: () => {
        throw new DOMException('Bloqueado', 'SecurityError');
      },
    });
    TestBed.configureTestingModule({
      providers: [{ provide: DOCUMENT, useValue: documentoCon(ventana) }],
    });

    expect(TestBed.inject(ALMACEN_MARCAS)).toBeNull();
  });

  it('solo acepta el mensaje de cierre y nunca transporta tokens', () => {
    expect(esMensajeDeCierre({ tipo: 'cerrada' })).toBe(true);
    expect(esMensajeDeCierre({ tipo: 'abierta' })).toBe(false);
    expect(esMensajeDeCierre('cerrada')).toBe(false);
    expect(esMensajeDeCierre(null)).toBe(false);
  });
});

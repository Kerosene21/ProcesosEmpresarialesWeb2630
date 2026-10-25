import { actividad, diagramaCompleto, lane, pool } from '../../testing/diagramas';
import { DiagramaProceso } from '../core/modelos/diagrama';
import { PosicionNodo } from '../core/modelos/movimiento';
import { calcularGeometria } from './geometria';
import { conLaneNueva, conLanesOrdenadas, conPool, recolocarContenido, sinLane } from './recolocacion';

function aplicar(diagrama: DiagramaProceso, nodos: PosicionNodo[]): DiagramaProceso {
  const nueva = (tipo: PosicionNodo['tipo'], id: number) => nodos.find((nodo) => nodo.tipo === tipo && nodo.id === id);
  const mover = <T extends { id: number; posicionY: number }>(tipo: PosicionNodo['tipo'], lista: T[]): T[] =>
    lista.map((elemento) => ({ ...elemento, posicionY: nueva(tipo, elemento.id)?.posicionY ?? elemento.posicionY }));
  return {
    ...diagrama,
    actividades: mover('ACTIVIDAD', diagrama.actividades),
    gateways: mover('GATEWAY', diagrama.gateways),
    messageThrows: mover('EVENTO', diagrama.messageThrows),
    messageCatches: mover('EVENTO', diagrama.messageCatches),
    enviosExternos: mover('EVENTO', diagrama.enviosExternos),
  };
}

describe('recolocación del contenido tras un cambio de estructura', () => {
  it('no propone cambios si la estructura no cambia', () => {
    const diagrama = diagramaCompleto();

    expect(recolocarContenido(diagrama, diagrama)).toEqual([]);
  });

  it('al reordenar las lanes, cada nodo acompaña a la lane en la que estaba', () => {
    const diagrama = diagramaCompleto();
    const despues = conLanesOrdenadas(diagrama, 80, [12, 11]);

    const nodos = recolocarContenido(diagrama, despues);

    expect(nodos).toEqual([
      { tipo: 'ACTIVIDAD', id: 1, posicionX: 160, posicionY: 200 },
      { tipo: 'ACTIVIDAD', id: 2, posicionX: 340, posicionY: 200 },
      { tipo: 'ACTIVIDAD', id: 3, posicionX: 620, posicionY: 60 },
      { tipo: 'GATEWAY', id: 4, posicionX: 520, posicionY: 210 },
      { tipo: 'EVENTO', id: 5, posicionX: 60, posicionY: 210 },
      { tipo: 'EVENTO', id: 7, posicionX: 820, posicionY: 210 },
    ]);
    expect(calcularGeometria(despues).advertencias.some((texto) => texto.includes('por encima'))).toBe(true);
    expect(calcularGeometria(aplicar(despues, nodos)).advertencias).toEqual([]);
  });

  it('al insertar una lane intermedia desplaza las lanes inferiores y los pools que quedan debajo', () => {
    const diagrama = diagramaCompleto();
    const despues = conLaneNueva(diagrama, 80, 1);

    const nodos = recolocarContenido(diagrama, despues);

    expect(nodos.map(({ tipo, id, posicionY }) => `${tipo}:${id}:${posicionY}`)).toEqual([
      'ACTIVIDAD:1:200',
      'ACTIVIDAD:2:200',
      'ACTIVIDAD:3:340',
      'GATEWAY:4:210',
      'EVENTO:6:520',
      'EVENTO:5:210',
      'EVENTO:7:210',
    ]);
    expect(calcularGeometria(aplicar(despues, nodos)).advertencias).toEqual([]);
  });

  it('al agregar una lane al final solo desplaza el contenido de los pools inferiores', () => {
    const diagrama = diagramaCompleto();

    expect(recolocarContenido(diagrama, conLaneNueva(diagrama, 80, null))).toEqual([
      { tipo: 'EVENTO', id: 6, posicionX: 60, posicionY: 520 },
    ]);
  });

  it('al dejar de ser caja negra un pool desplaza el contenido de los pools que tiene debajo', () => {
    const diagrama = diagramaCompleto();
    diagrama.pools = [
      pool(80, 'Empresa de prueba', 1),
      pool(82, 'Servicio de correo', 2, { cajaNegra: true }),
      pool(81, 'Cliente', 3),
    ];
    diagrama.messageThrows = [{ ...diagrama.messageThrows[0], posicionY: 460 }];
    expect(calcularGeometria(diagrama).advertencias).toEqual([]);

    const nodos = recolocarContenido(diagrama, conPool(diagrama, 82, { cajaNegra: false }));

    expect(nodos).toEqual([{ tipo: 'EVENTO', id: 6, posicionX: 60, posicionY: 520 }]);
  });

  it('al eliminar una lane vacía sube el contenido de las lanes inferiores', () => {
    const diagrama = diagramaCompleto();
    diagrama.lanes = [lane(11, 'General', 1), lane(13, 'Vacía', 2), lane(12, 'Supervisor', 3, 21)];
    diagrama.actividades[2] = actividad(3, 'Aprobar solicitud', 12, 620, 340);
    diagrama.messageThrows = [{ ...diagrama.messageThrows[0], posicionY: 480 }];
    expect(calcularGeometria(diagrama).advertencias).toEqual([]);

    const nodos = recolocarContenido(diagrama, sinLane(diagrama, 13));

    expect(nodos).toContainEqual({ tipo: 'ACTIVIDAD', id: 3, posicionX: 620, posicionY: 200 });
    expect(nodos.find((nodo) => nodo.tipo === 'ACTIVIDAD' && nodo.id === 1)).toBeUndefined();
  });
});

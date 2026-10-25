import { diagramaCompleto } from '../../testing/diagramas';
import { arcoEntre, arcosConectados, destinosPermitidos, motivoRechazo } from './conexiones';
import { NodoDiagrama, calcularGeometria } from './geometria';

describe('reglas de conexión del modelador', () => {
  const diagrama = diagramaCompleto();
  const nodos = calcularGeometria(diagrama).nodos;
  const nodo = (tipo: NodoDiagrama['elemento']['tipo'], id: number) =>
    nodos.find((actual) => actual.elemento.tipo === tipo && actual.elemento.id === id)!;

  it('rechaza las autoconexiones, los pools distintos y los Message Catch de inicio como destino', () => {
    const registrar = nodo('ACTIVIDAD', 1);

    expect(motivoRechazo(registrar, registrar)).toBe('El origen y el destino no pueden ser el mismo nodo');
    expect(motivoRechazo(nodo('MESSAGE_THROW', 6), registrar)).toContain('están en pools distintos');
    expect(motivoRechazo(registrar, nodo('MESSAGE_CATCH', 5))).toContain('no admite flujos de secuencia entrantes');
    expect(motivoRechazo(registrar, nodo('GATEWAY', 4))).toBeNull();
  });

  it('ofrece como destino solo los nodos del mismo pool que admiten entradas', () => {
    const destinos = destinosPermitidos(nodos, nodo('ACTIVIDAD', 1)).map((actual) => actual.nombre);

    expect(destinos).toEqual([
      'Evaluar solicitud',
      'Aprobar solicitud',
      'Gateway EXCLUSIVO #4',
      'Envío externo: Notificación de resultado',
    ]);
  });

  it('detecta el arco existente entre dos nodos y los arcos conectados a un nodo', () => {
    const evaluar = nodo('ACTIVIDAD', 2);
    const decision = nodo('GATEWAY', 4);

    expect(arcoEntre(diagrama.arcos, evaluar, decision)?.id).toBe(33);
    expect(arcoEntre(diagrama.arcos, evaluar, decision, 33)).toBeNull();
    expect(arcoEntre(diagrama.arcos, decision, evaluar)).toBeNull();
    expect(arcosConectados(diagrama.arcos, decision).map((arco) => arco.id)).toEqual([33, 34, 35]);
  });
});

import { diagramaCompleto, lane } from '../../testing/diagramas';
import { DestinoMovimiento, NodoDiagrama, calcularGeometria } from './geometria';
import { motivoMovimientoNoValido, movimientoSinCambios } from './movimientos';

describe('reglas de movimiento del modelador', () => {
  const diagrama = diagramaCompleto();
  const geometria = calcularGeometria(diagrama);
  const nodo = (tipo: NodoDiagrama['elemento']['tipo'], id: number) =>
    geometria.nodos.find((actual) => actual.elemento.tipo === tipo && actual.elemento.id === id)!;
  const [propietario, cliente, correo] = diagrama.pools;
  const ventas = { ...lane(40, 'Ventas', 1), poolId: cliente.id };

  function destino(cambios: Partial<DestinoMovimiento>): DestinoMovimiento {
    return { pool: propietario, lane: diagrama.lanes[1], posicion: { x: 340, y: 190 }, ...cambios };
  }

  it('admite mover una actividad a otra lane de su pool', () => {
    expect(motivoMovimientoNoValido(geometria, nodo('ACTIVIDAD', 2), destino({}), diagrama.arcos)).toBeNull();
  });

  it('exige que una actividad quede dentro de una lane y fuera de cajas negras', () => {
    expect(motivoMovimientoNoValido(geometria, nodo('ACTIVIDAD', 2), null, diagrama.arcos)).toBe(
      'Suelta «Evaluar solicitud» dentro de un pool del diagrama.',
    );
    expect(motivoMovimientoNoValido(geometria, nodo('ACTIVIDAD', 2), destino({ pool: correo, lane: null }), diagrama.arcos)).toBe(
      'El pool «Servicio de correo» es una caja negra y no admite elementos.',
    );
    expect(motivoMovimientoNoValido(geometria, nodo('ACTIVIDAD', 2), destino({ lane: null }), diagrama.arcos)).toBe(
      'La actividad «Evaluar solicitud» debe quedar dentro de una lane.',
    );
  });

  it('impide pasar a otro pool una actividad con arcos y lo permite si no tiene ninguno', () => {
    expect(motivoMovimientoNoValido(geometria, nodo('ACTIVIDAD', 2), destino({ pool: cliente, lane: ventas }), diagrama.arcos)).toBe(
      '«Evaluar solicitud» tiene 2 arcos conectados: no puede pasar a otro pool porque un flujo de secuencia no cruza pools.',
    );
    expect(motivoMovimientoNoValido(geometria, nodo('ACTIVIDAD', 2), destino({ pool: cliente, lane: ventas }), [])).toBeNull();
  });

  it('mantiene gateways y eventos en su propio pool', () => {
    expect(motivoMovimientoNoValido(geometria, nodo('GATEWAY', 4), destino({ pool: cliente, lane: null }), diagrama.arcos)).toBe(
      '«Gateway EXCLUSIVO #4» conserva su pool al moverlo: suéltalo dentro de «Empresa de prueba».',
    );
    expect(motivoMovimientoNoValido(geometria, nodo('MESSAGE_THROW', 6), destino({ pool: cliente, lane: null }), diagrama.arcos)).toBeNull();
    expect(motivoMovimientoNoValido(geometria, nodo('MESSAGE_THROW', 6), destino({}), diagrama.arcos)).toContain(
      '«Message Throw: Solicitud de servicio» conserva su pool al moverlo: suéltalo dentro de «Cliente».',
    );
  });

  it('reconoce un movimiento que no cambia ni la lane ni la posición', () => {
    expect(movimientoSinCambios(nodo('ACTIVIDAD', 2), destino({ lane: diagrama.lanes[0], posicion: { x: 340, y: 60 } }))).toBe(true);
    expect(movimientoSinCambios(nodo('ACTIVIDAD', 2), destino({ posicion: { x: 340, y: 60 } }))).toBe(false);
    expect(movimientoSinCambios(nodo('GATEWAY', 4), destino({ posicion: { x: 520, y: 70 } }))).toBe(true);
  });
});

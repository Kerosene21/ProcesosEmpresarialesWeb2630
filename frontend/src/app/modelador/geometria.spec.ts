import { actividad, arco, diagramaCompleto, diagramaInicial, pool } from '../../testing/diagramas';
import {
  ALTO_CAJA_NEGRA,
  ALTO_MINIMO_LANE,
  bandaDeLane,
  bandaDePool,
  buscarNodo,
  cajaDeNodo,
  calcularGeometria,
  centro,
  colocarEnBanda,
  existeElemento,
  llegada,
  posicionLibre,
  puntosRombo,
  recortar,
  ubicacionEn,
} from './geometria';

describe('geometría del diagrama', () => {
  it('usa las mismas medidas y el mismo punto de llegada que GeometriaArco del backend', () => {
    const registrar = centro(cajaDeNodo('ACTIVIDAD', 160, 60));
    const evaluar = centro(cajaDeNodo('ACTIVIDAD', 340, 60));
    const decision = centro(cajaDeNodo('GATEWAY', 520, 70));
    const aprobar = centro(cajaDeNodo('ACTIVIDAD', 620, 200));

    expect(registrar).toEqual({ x: 225, y: 83 });
    expect(llegada(registrar, evaluar, cajaDeNodo('ACTIVIDAD', 340, 60))).toEqual({ x: 340, y: 83 });
    expect(llegada(evaluar, decision, cajaDeNodo('GATEWAY', 520, 70))).toEqual({ x: 520, y: 91 });
    expect(llegada(decision, aprobar, cajaDeNodo('ACTIVIDAD', 620, 200))).toEqual({ x: 660, y: 200 });
    expect(llegada(registrar, registrar, cajaDeNodo('ACTIVIDAD', 160, 60))).toEqual(registrar);
  });

  it('dibuja el pool y la lane iniciales aunque el proceso no tenga elementos', () => {
    const geometria = calcularGeometria(diagramaInicial());

    expect(geometria.nodos).toEqual([]);
    expect(geometria.pools).toHaveLength(1);
    expect(geometria.pools[0]).toMatchObject({ x: -60, y: 0, ancho: 940, alto: ALTO_MINIMO_LANE });
    expect(geometria.pools[0].lanes[0]).toMatchObject({ x: -28, y: 0, ancho: 908, alto: ALTO_MINIMO_LANE });
    expect(geometria.vista).toEqual({ x: -76, y: -16, ancho: 972, alto: 172 });
    expect(geometria.advertencias).toEqual([]);
  });

  it('apila pools y lanes en su orden y los ajusta a las coordenadas absolutas de sus nodos', () => {
    const geometria = calcularGeometria(diagramaCompleto());
    const [propietario, cliente, correo] = geometria.pools;

    expect(geometria.pools.map((banda) => banda.pool.nombre)).toEqual(['Empresa de prueba', 'Cliente', 'Servicio de correo']);
    expect(propietario).toMatchObject({ y: 0, alto: 280 });
    expect(propietario.lanes.map(({ lane, y, alto }) => ({ nombre: lane.nombre, y, alto }))).toEqual([
      { nombre: 'General', y: 0, alto: 140 },
      { nombre: 'Supervisor', y: 140, alto: 140 },
    ]);
    expect(cliente).toMatchObject({ y: 320, alto: 140, lanes: [] });
    expect(correo).toMatchObject({ y: 500, alto: ALTO_CAJA_NEGRA });
    expect(geometria.vista).toEqual({ x: -76, y: -16, ancho: 972, alto: 612 });
    expect(geometria.advertencias).toEqual([]);
  });

  it('reutiliza las coordenadas de los arcos calculadas por el backend sin recalcularlas', () => {
    const geometria = calcularGeometria(diagramaCompleto());

    expect(geometria.arcos.find(({ arco }) => arco.id === 34)?.segmento).toEqual({
      desde: { x: 543, y: 93 },
      hasta: { x: 660, y: 200 },
    });
  });

  it('dirige los flujos de mensaje al Message Catch homólogo o al borde del pool destino', () => {
    const [solicitud, notificacion] = calcularGeometria(diagramaCompleto()).flujos;

    expect(solicitud.segmento).toEqual({ desde: { x: 78, y: 380 }, hasta: { x: 78, y: 106 } });
    expect(notificacion.segmento).toEqual({ desde: { x: 838, y: 106 }, hasta: { x: 838, y: 500 } });
  });

  it('crece hacia arriba y a la izquierda para no cortar nodos con coordenadas negativas', () => {
    const diagrama = diagramaInicial();
    diagrama.actividades = [actividad(1, 'Fuera de margen', 11, -50, -30)];

    const geometria = calcularGeometria(diagrama);

    expect(geometria.inicioContenido).toBe(-70);
    expect(geometria.pools[0].y).toBe(-50);
    expect(geometria.vista.x).toBe(-146);
    expect(geometria.vista.y).toBe(-66);
  });

  it('advierte de los datos inconsistentes sin ocultar los elementos', () => {
    const diagrama = diagramaInicial();
    diagrama.pools.push(pool(81, 'Cliente', 2));
    diagrama.lanes.push({ id: 13, nombre: 'Externa', rolProcesoId: null, poolId: 81, orden: 1 });
    diagrama.actividades = [
      actividad(1, 'En otra lane', 99, 100, 40),
      actividad(2, 'Sobre el pool anterior', 13, 100, 20, { poolId: 81 }),
      actividad(3, 'Sin pool', 11, 300, 40, { poolId: 70 }),
    ];
    diagrama.arcos = [arco(40, ['ACTIVIDAD', 1, 'En otra lane'], ['ACTIVIDAD', 9, 'ACTIVIDAD #9'], null)];
    diagrama.flujosMensaje = [
      { tipoEvento: 'MESSAGE_THROW', eventoId: 90, nombreMensaje: 'Perdido', poolOrigenId: 80, poolDestinoId: 81, catchDestinoId: null },
    ];

    const geometria = calcularGeometria(diagrama);

    expect(geometria.nodos).toHaveLength(3);
    expect(geometria.arcos[0].segmento).toBeNull();
    expect(geometria.flujos[0].segmento).toBeNull();
    expect(geometria.advertencias).toEqual([
      '«Sobre el pool anterior» está por encima de la lane «Externa» a la que pertenece',
      '«En otra lane» pertenece a una lane que no aparece en el diagrama',
      '«Sin pool» pertenece a un pool que no aparece en el diagrama',
      'El arco «En otra lane → ACTIVIDAD #9» conecta un elemento que no aparece en el diagrama',
      'El arco «En otra lane → ACTIVIDAD #9» no tiene coordenadas y no se puede dibujar',
      'El flujo de mensaje «Perdido» no tiene un origen o un destino en el diagrama',
    ]);
  });

  it('localiza el pool y la lane bajo un punto del lienzo y rechaza la cabecera', () => {
    const geometria = calcularGeometria(diagramaCompleto());

    expect(ubicacionEn(geometria, { x: 300, y: 200 })).toMatchObject({ pool: { pool: { id: 80 } }, lane: { lane: { id: 12 } } });
    expect(ubicacionEn(geometria, { x: 300, y: 400 })).toMatchObject({ pool: { pool: { id: 81 } }, lane: null });
    expect(ubicacionEn(geometria, { x: 300, y: 300 })).toBeNull();
    expect(ubicacionEn(geometria, { x: -10, y: 60 })).toBeNull();
  });

  it('encuentra nodos por su selección y comprueba si un elemento sigue en el diagrama', () => {
    const diagrama = diagramaCompleto();
    const geometria = calcularGeometria(diagrama);

    expect(buscarNodo(geometria, { tipo: 'MESSAGE_CATCH', id: 5 })).toMatchObject({ tipoFlujo: 'EVENTO', admiteEntradas: false });
    expect(buscarNodo(geometria, { tipo: 'GATEWAY', id: 4 })?.exigeCondicion).toBe(true);
    expect(buscarNodo(geometria, { tipo: 'ARCO', id: 31 })).toBeNull();
    expect(existeElemento(diagrama, { tipo: 'ARCO', id: 31 })).toBe(true);
    expect(existeElemento(diagrama, { tipo: 'ACTIVIDAD', id: 99 })).toBe(false);
  });

  it('genera los vértices del rombo y recorta los textos largos', () => {
    expect(puntosRombo(cajaDeNodo('GATEWAY', 520, 70))).toBe('543,70 566,93 543,116 520,93');
    expect(recortar('Registrar solicitud del cliente', 12)).toBe('Registrar s…');
    expect(recortar('Corto', 12)).toBe('Corto');
  });

  it('centra el nodo en el punto pulsado sin salir de su banda ni invadir la cabecera', () => {
    const geometria = calcularGeometria(diagramaCompleto());
    const supervisor = bandaDeLane(geometria, 12)!;

    expect(supervisor).toMatchObject({ y: 140, alto: 140 });
    expect(colocarEnBanda('ACTIVIDAD', { x: 300, y: 210 }, supervisor, geometria.inicioContenido)).toEqual({ x: 235, y: 187 });
    expect(colocarEnBanda('ACTIVIDAD', { x: 10, y: 141 }, supervisor, geometria.inicioContenido)).toEqual({ x: 20, y: 160 });
    expect(colocarEnBanda('ACTIVIDAD', { x: 300, y: 279 }, supervisor, geometria.inicioContenido)).toEqual({ x: 235, y: 214 });
    expect(colocarEnBanda('GATEWAY', { x: 300, y: 400 }, bandaDePool(geometria, 81)!, geometria.inicioContenido)).toEqual({ x: 277, y: 377 });
  });

  it('propone una posición libre a la derecha de los nodos de la banda', () => {
    const geometria = calcularGeometria(diagramaCompleto());

    expect(posicionLibre(geometria, 'ACTIVIDAD', bandaDeLane(geometria, 12)!)).toEqual({ x: 790, y: 187 });
    expect(posicionLibre(geometria, 'GATEWAY', bandaDePool(geometria, 81)!)).toEqual({ x: 136, y: 367 });
    expect(posicionLibre(calcularGeometria(diagramaInicial()), 'ACTIVIDAD', calcularGeometria(diagramaInicial()).pools[0].lanes[0])).toEqual({ x: 20, y: 47 });
    expect(bandaDeLane(geometria, 99)).toBeNull();
    expect(bandaDePool(geometria, 99)).toBeNull();
  });
});

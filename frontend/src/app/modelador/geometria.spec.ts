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
  desplazarNodo,
  existeElemento,
  llegada,
  posicionLibre,
  puntosRombo,
  recortar,
  reubicarEnLane,
  sinElemento,
  ubicacionEn,
  ubicarMovimiento,
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

  describe('cambio de lane y movimiento de nodos', () => {
    function conActividad(id: number, laneId: number, posicionX: number, posicionY: number) {
      const diagrama = diagramaCompleto();
      diagrama.actividades = diagrama.actividades.map((actual) =>
        actual.id === id ? { ...actual, laneId, posicionX, posicionY } : actual,
      );
      return diagrama;
    }

    it('al bajar de General a Supervisor recoloca la actividad dentro de la nueva lane', () => {
      const diagrama = diagramaCompleto();
      const geometria = calcularGeometria(diagrama);

      const posicion = reubicarEnLane(diagrama, geometria, { tipo: 'ACTIVIDAD', id: 2 }, 12);

      expect(posicion).toEqual({ x: 340, y: 200 });
      expect(calcularGeometria(conActividad(2, 12, 340, 60)).advertencias).toContain(
        '«Evaluar solicitud» está por encima de la lane «Supervisor» a la que pertenece',
      );
      expect(calcularGeometria(conActividad(2, 12, posicion!.x, posicion!.y)).advertencias).toEqual([]);
    });

    it('al subir de Supervisor a General no agranda General ni desplaza la lane inferior', () => {
      const diagrama = diagramaCompleto();
      const geometria = calcularGeometria(diagrama);

      const posicion = reubicarEnLane(diagrama, geometria, { tipo: 'ACTIVIDAD', id: 3 }, 11);
      const resultado = calcularGeometria(conActividad(3, 11, posicion!.x, posicion!.y));

      expect(posicion).toEqual({ x: 620, y: 60 });
      expect(resultado.pools[0].lanes.map(({ y, alto }) => ({ y, alto }))).toEqual([
        { y: 0, alto: 140 },
        { y: 140, alto: 140 },
      ]);
      expect(resultado.advertencias).toEqual([]);
    });

    it('evita solapar otra actividad de la lane destino buscando un hueco libre', () => {
      const diagrama = conActividad(3, 12, 160, 200);
      const geometria = calcularGeometria(diagrama);

      expect(reubicarEnLane(diagrama, geometria, { tipo: 'ACTIVIDAD', id: 1 }, 12)).toEqual({ x: 330, y: 187 });
      expect(reubicarEnLane(diagrama, geometria, { tipo: 'ACTIVIDAD', id: 1 }, 99)).toBeNull();
    });

    it('quita del diagrama solo el elemento indicado', () => {
      const diagrama = diagramaCompleto();

      expect(sinElemento(diagrama, { tipo: 'ACTIVIDAD', id: 2 }).actividades.map((actual) => actual.id)).toEqual([1, 3]);
      expect(sinElemento(diagrama, { tipo: 'MESSAGE_CATCH', id: 5 }).messageCatches).toEqual([]);
      expect(sinElemento(diagrama, { tipo: 'MESSAGE_CATCH', id: 5 }).actividades).toHaveLength(3);
    });

    it('al soltar dentro de la misma lane conserva el punto elegido', () => {
      const diagrama = diagramaCompleto();

      expect(ubicarMovimiento(diagrama, calcularGeometria(diagrama), { tipo: 'ACTIVIDAD', id: 2 }, { x: 360, y: 50 })).toMatchObject({
        pool: { id: 80 },
        lane: { id: 11 },
        posicion: { x: 360, y: 50 },
      });
    });

    it('al cruzar el límite de una lane asigna la lane de destino y no sale de su banda', () => {
      const diagrama = diagramaCompleto();
      const geometria = calcularGeometria(diagrama);

      expect(ubicarMovimiento(diagrama, geometria, { tipo: 'ACTIVIDAD', id: 2 }, { x: 340, y: 190 })).toMatchObject({
        lane: { id: 12 },
        posicion: { x: 340, y: 190 },
      });
      expect(ubicarMovimiento(diagrama, geometria, { tipo: 'ACTIVIDAD', id: 2 }, { x: 340, y: 240 })?.posicion).toEqual({ x: 340, y: 214 });
      expect(ubicarMovimiento(diagrama, geometria, { tipo: 'ACTIVIDAD', id: 2 }, { x: -200, y: 60 })).toBeNull();
      expect(ubicarMovimiento(diagrama, geometria, { tipo: 'ACTIVIDAD', id: 2 }, { x: 340, y: 2000 })).toBeNull();
    });

    it('calcula la posición con las bandas que quedan cuando la lane de origen se encoge', () => {
      const diagrama = diagramaCompleto();
      diagrama.actividades = [
        actividad(1, 'Registrar solicitud', 11, 340, 180),
        actividad(3, 'Aprobar solicitud', 12, 620, 300),
      ];
      diagrama.gateways = [];
      diagrama.arcos = [];
      diagrama.messageThrows = [{ ...diagrama.messageThrows[0], posicionY: 460 }];
      const geometria = calcularGeometria(diagrama);
      expect(bandaDeLane(geometria, 12)?.y).toBe(246);
      expect(geometria.advertencias).toEqual([]);

      const destino = ubicarMovimiento(diagrama, geometria, { tipo: 'ACTIVIDAD', id: 1 }, { x: 340, y: 300 });
      diagrama.actividades[0] = { ...diagrama.actividades[0], laneId: 12, posicionX: destino!.posicion.x, posicionY: destino!.posicion.y };

      expect(destino).toMatchObject({ lane: { id: 12 }, posicion: { x: 340, y: 194 } });
      expect(calcularGeometria(diagrama).advertencias).toEqual([]);
    });

    it('identifica el pool de destino de gateways y eventos, incluida una caja negra', () => {
      const diagrama = diagramaCompleto();
      const geometria = calcularGeometria(diagrama);

      expect(ubicarMovimiento(diagrama, geometria, { tipo: 'GATEWAY', id: 4 }, { x: 560, y: 180 })).toMatchObject({
        pool: { id: 80 },
        lane: { id: 12 },
        posicion: { x: 560, y: 180 },
      });
      expect(ubicarMovimiento(diagrama, geometria, { tipo: 'GATEWAY', id: 4 }, { x: 300, y: 520 })).toMatchObject({
        pool: { id: 82, cajaNegra: true },
        lane: null,
      });
      expect(ubicarMovimiento(diagrama, geometria, { tipo: 'MESSAGE_THROW', id: 6 }, { x: 200, y: 390 })).toMatchObject({
        pool: { id: 81 },
        lane: null,
        posicion: { x: 200, y: 390 },
      });
    });

    it('al desplazar un nodo recalcula solo los arcos y flujos conectados a él', () => {
      const geometria = calcularGeometria(diagramaCompleto());

      const movida = desplazarNodo(geometria, { tipo: 'ACTIVIDAD', id: 1 }, { x: 160, y: 100 });

      expect(buscarNodo(movida, { tipo: 'ACTIVIDAD', id: 1 })?.caja).toMatchObject({ x: 160, y: 100 });
      expect(movida.arcos.find(({ arco }) => arco.id === 32)?.segmento).toEqual({
        desde: { x: 225, y: 123 },
        hasta: { x: 340, y: 97 },
      });
      expect(movida.arcos.find(({ arco }) => arco.id === 33)).toBe(geometria.arcos.find(({ arco }) => arco.id === 33));
      expect(movida.pools).toBe(geometria.pools);

      const catchMovido = desplazarNodo(geometria, { tipo: 'MESSAGE_CATCH', id: 5 }, { x: 200, y: 70 });
      expect(catchMovido.flujos[0].segmento?.hasta).not.toEqual(geometria.flujos[0].segmento?.hasta);
      expect(catchMovido.flujos[1]).toBe(geometria.flujos[1]);
      expect(desplazarNodo(geometria, { tipo: 'ACTIVIDAD', id: 99 }, { x: 0, y: 0 })).toBe(geometria);
    });
  });
});

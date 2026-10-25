export interface CrearLane {
  rolProcesoId: number;
  orden: number | null;
}

export type EditarLane = CrearLane;

export interface ReordenarLanes {
  lanes: number[];
}

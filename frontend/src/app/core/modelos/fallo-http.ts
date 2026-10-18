export interface ErrorApi {
  codigo: string;
  mensaje: string;
  procesos?: string[];
}

export interface ErrorValidacion {
  timestamp: string;
  status: number;
  error: string;
  path: string;
}

export type TipoFallo =
  | 'conexion'
  | 'no-autenticado'
  | 'sin-permiso'
  | 'no-encontrado'
  | 'datos-no-validos'
  | 'conflicto'
  | 'servidor'
  | 'desconocido';

export interface FalloHttp {
  tipo: TipoFallo;
  estado: number;
  codigo: string | null;
  titulo: string;
  detalle: string | null;
}

import { RolUsuario } from './sesion';

export interface PermisoEstructura {
  rol: RolUsuario;
  configurable: boolean;
  crearPool: boolean;
  editarPool: boolean;
  eliminarPool: boolean;
  crearLane: boolean;
  editarLane: boolean;
  eliminarLane: boolean;
}

export type ConfigurarPermisoEstructura = Omit<PermisoEstructura, 'rol' | 'configurable'>;

export type OperacionEstructura = keyof ConfigurarPermisoEstructura;

export interface DescripcionOperacion {
  clave: OperacionEstructura;
  elemento: 'Pools' | 'Lanes';
  accion: 'Crear' | 'Editar' | 'Eliminar';
}

export const OPERACIONES_ESTRUCTURA: readonly DescripcionOperacion[] = [
  { clave: 'crearPool', elemento: 'Pools', accion: 'Crear' },
  { clave: 'editarPool', elemento: 'Pools', accion: 'Editar' },
  { clave: 'eliminarPool', elemento: 'Pools', accion: 'Eliminar' },
  { clave: 'crearLane', elemento: 'Lanes', accion: 'Crear' },
  { clave: 'editarLane', elemento: 'Lanes', accion: 'Editar' },
  { clave: 'eliminarLane', elemento: 'Lanes', accion: 'Eliminar' },
];

export const SIN_PERMISOS_ESTRUCTURA: ConfigurarPermisoEstructura = {
  crearPool: false,
  editarPool: false,
  eliminarPool: false,
  crearLane: false,
  editarLane: false,
  eliminarLane: false,
};

export function operacionesDe(permiso: PermisoEstructura): ConfigurarPermisoEstructura {
  const { crearPool, editarPool, eliminarPool, crearLane, editarLane, eliminarLane } = permiso;
  return { crearPool, editarPool, eliminarPool, crearLane, editarLane, eliminarLane };
}

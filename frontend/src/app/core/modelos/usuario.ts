import { RolUsuario } from './sesion';

export const ROLES_USUARIO: readonly RolUsuario[] = ['ADMINISTRADOR', 'EDITOR', 'SOLO_LECTURA'];

export const LIMITES_USUARIO = {
  correo: 180,
  passwordMinima: 8,
  passwordMaxima: 100,
} as const;

export interface Usuario {
  id: number;
  correo: string;
  rol: RolUsuario;
  activo: boolean;
}

export interface CrearUsuario {
  correo: string;
  password: string;
  rol: RolUsuario;
}

export interface CambiarRolUsuario {
  rol: RolUsuario;
}

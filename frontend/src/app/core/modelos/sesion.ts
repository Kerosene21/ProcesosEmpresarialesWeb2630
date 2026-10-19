export type RolUsuario = 'ADMINISTRADOR' | 'EDITOR' | 'SOLO_LECTURA';

export const ROLES_ESCRITURA: readonly RolUsuario[] = ['ADMINISTRADOR', 'EDITOR'];

export interface Sesion {
  autenticado: boolean;
  correo: string | null;
  rol: RolUsuario | null;
  empresaId: number | null;
  empresaNombre: string | null;
}

export interface Credenciales {
  correo: string;
  password: string;
}

export const SESION_ANONIMA: Sesion = {
  autenticado: false,
  correo: null,
  rol: null,
  empresaId: null,
  empresaNombre: null,
};

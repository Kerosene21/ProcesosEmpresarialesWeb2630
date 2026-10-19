export interface Empresa {
  id: number;
  nombre: string;
  nit: string;
  correoContacto: string;
  administradorUsername: string | null;
}

export interface RegistroEmpresa {
  nombre: string;
  nit: string;
  correoContacto: string;
  passwordAdministrador: string;
}

export const LIMITES_EMPRESA = {
  nombre: 150,
  nit: 20,
  correoContacto: 180,
  passwordMinimo: 8,
  passwordMaximo: 100,
} as const;

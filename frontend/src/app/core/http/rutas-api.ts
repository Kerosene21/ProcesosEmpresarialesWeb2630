export const RUTAS_AUTENTICACION = {
  csrf: '/api/auth/csrf',
  login: '/api/auth/login',
  refresh: '/api/auth/refresh',
  logout: '/api/auth/logout',
  me: '/api/auth/me',
} as const;

const PREFIJO_API = '/api/';

const RUTAS_DE_CREDENCIALES: ReadonlySet<string> = new Set([
  RUTAS_AUTENTICACION.csrf,
  RUTAS_AUTENTICACION.login,
  RUTAS_AUTENTICACION.refresh,
  RUTAS_AUTENTICACION.logout,
]);

const ORIGEN_RELATIVO = 'http://relativo.invalid';

export function rutaDeApiPropia(url: string): string | null {
  const ruta = esRelativa(url) ? rutaDe(url, ORIGEN_RELATIVO) : rutaAbsolutaPropia(url);
  return ruta !== null && ruta.startsWith(PREFIJO_API) ? ruta : null;
}

function esRelativa(url: string): boolean {
  return url.startsWith('/') && !url.startsWith('//') && !url.startsWith('/\\');
}

function rutaAbsolutaPropia(url: string): string | null {
  const origen = window.location.origin;
  if (!origen || origen === 'null') {
    return null;
  }
  try {
    const destino = new URL(url);
    return destino.origin === origen ? destino.pathname : null;
  } catch {
    return null;
  }
}

function rutaDe(url: string, base: string): string | null {
  try {
    return new URL(url, base).pathname;
  } catch {
    return null;
  }
}

export function esRutaDeCredenciales(ruta: string): boolean {
  return RUTAS_DE_CREDENCIALES.has(ruta);
}

export function requiereBearer(url: string): boolean {
  const ruta = rutaDeApiPropia(url);
  return ruta !== null && !esRutaDeCredenciales(ruta);
}

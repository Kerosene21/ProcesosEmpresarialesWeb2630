import { esRutaDeCredenciales, requiereBearer, rutaDeApiPropia } from './rutas-api';

describe('rutas de la API', () => {
  it('reconoce las rutas relativas de la API sin la consulta ni el fragmento', () => {
    expect(rutaDeApiPropia('/api/procesos')).toBe('/api/procesos');
    expect(rutaDeApiPropia('/api/procesos/5?page=2#arriba')).toBe('/api/procesos/5');
    expect(rutaDeApiPropia('/api/auth/me/../refresh')).toBe('/api/auth/refresh');
  });

  it('descarta otros orígenes, rutas ajenas a la API y URL no válidas', () => {
    expect(rutaDeApiPropia('https://externo.example.com/api/procesos')).toBeNull();
    expect(rutaDeApiPropia('//externo.example.com/api/procesos')).toBeNull();
    expect(rutaDeApiPropia('/\\externo.example.com/api/procesos')).toBeNull();
    expect(rutaDeApiPropia('/assets/logo.svg')).toBeNull();
    expect(rutaDeApiPropia('/apiprocesos')).toBeNull();
    expect(rutaDeApiPropia('api/procesos')).toBeNull();
  });

  it('acepta una URL absoluta solo si es del mismo origen que la aplicación', () => {
    const origen = window.location.origin;
    if (origen && origen !== 'null') {
      expect(rutaDeApiPropia(`${origen}/api/procesos`)).toBe('/api/procesos');
    }
  });

  it('distingue los endpoints de credenciales de los protegidos', () => {
    for (const ruta of [
      '/api/auth/csrf',
      '/api/auth/login',
      '/api/auth/refresh',
      '/api/auth/logout',
      '/api/sesion',
    ]) {
      expect(esRutaDeCredenciales(ruta)).toBe(true);
      expect(requiereBearer(ruta)).toBe(false);
    }
    expect(esRutaDeCredenciales('/api/auth/me')).toBe(false);
    expect(requiereBearer('/api/auth/me')).toBe(true);
    expect(requiereBearer('/api/auth/refresh?x=1')).toBe(false);
    expect(requiereBearer('/api/procesos')).toBe(true);
  });
});

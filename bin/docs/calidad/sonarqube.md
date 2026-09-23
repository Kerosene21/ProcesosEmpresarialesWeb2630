# SonarQube Cloud

## Objetivo

Medir de forma continua y objetiva la calidad del código del proyecto, sin depender de que
alguien recuerde revisarlo. Cada cambio que llega a `main` pasa por un análisis que reporta
seguridad, fiabilidad, mantenibilidad, cobertura de pruebas y duplicación, y lo compara contra
un **Quality Gate**.

El análisis no reemplaza la revisión de código: la complementa señalando problemas mecánicos
(código muerto, complejidad, vulnerabilidades conocidas, falta de pruebas) para que la revisión
humana se concentre en el diseño y en la lógica de negocio.

## Arquitectura del análisis

```
Código
  → Maven          (mvn verify)
  → Tests          (Surefire ejecuta la suite)
  → JaCoCo         (instrumenta y produce el reporte de cobertura)
  → Sonar          (sonar-maven-plugin sube código + cobertura)
  → Quality Gate   (SonarQube Cloud aprueba o rechaza)
```

Los pasos ocurren en un solo comando de Maven. El orden importa: Sonar solo ve cobertura si
JaCoCo ya escribió su reporte, y JaCoCo solo tiene datos si los tests corrieron antes.

## Configuración del proyecto

La identidad del proyecto ante SonarQube Cloud vive en las `<properties>` del `pom.xml`, no en
el workflow, para que el mismo comando funcione igual en CI y en local:

| Propiedad | Valor |
|---|---|
| `sonar.organization` | `kerosene21` |
| `sonar.projectKey` | `Kerosene21_ProcesosEmpresarialesWeb2630` |

Ninguno de los dos es un dato sensible: identifican el proyecto, no autorizan nada.

El **análisis automático de SonarQube Cloud está desactivado**. Se usa análisis basado en CI,
porque es el único modo que puede recibir el reporte de cobertura de JaCoCo: el análisis
automático lee el repositorio pero nunca ejecuta las pruebas, así que reportaría 0% de cobertura.

## GitHub Actions

El workflow es [`.github/workflows/sonar.yml`](../../.github/workflows/sonar.yml) y se dispara en:

- `push` a `main`
- `pull_request` hacia `main`
- `workflow_dispatch` (ejecución manual desde la pestaña Actions)

No se analizan las ramas de feature por `push`. El plan gratuito de SonarQube Cloud tiene
limitaciones en el análisis de ramas, así que el punto de control es el **pull request hacia
`main`**: ahí es donde el equipo ve el resultado antes de integrar.

### Por qué el workflow levanta PostgreSQL

Porque la suite de pruebas lo necesita. `src/test/resources/application-test.properties` apunta
a una base PostgreSQL real:

```
jdbc:postgresql://localhost:5432/procesos_empresariales_test
```

La prueba `ProcesosEmpresarialesWeb2630ApplicationTests` levanta el contexto completo de Spring
con JPA y `ddl-auto=create-drop`, de modo que sin una base disponible el `mvn verify` falla y el
análisis nunca llega a ejecutarse.

En vez de modificar la configuración de pruebas para acomodar a CI, el workflow le da a CI lo
que la configuración ya espera: un **service container** `postgres:16-alpine` con la base
`procesos_empresariales_test`, el usuario `procesos_app` y el puerto `5432` mapeado, más un
health check con `pg_isready` para que el job no arranque antes de que la base acepte conexiones.

La contraseña de ese contenedor (`ci_test_password`) está escrita en el workflow a propósito.
No es una credencial: existe solo dentro del runner efímero de GitHub Actions, protege una base
que se crea y se destruye en cada ejecución, y no da acceso a ningún recurso persistente. Por eso
**no** se guarda como secreto: convertirla en secreto sugeriría que protege algo, y no es el caso.

Las contraseñas reales de desarrollo siguen fuera del repositorio, en `.env` (ignorado por Git).

## Cobertura

**JaCoCo** se engancha al build mediante `jacoco-maven-plugin`, con dos ejecuciones:

| Ejecución | Fase | Qué hace |
|---|---|---|
| `prepare-agent` | `initialize` | Inyecta el agente Java que registra qué líneas ejecutan las pruebas |
| `report` | `verify` | Convierte los datos crudos (`target/jacoco.exec`) en reportes legibles |

El resultado que importa es:

```
target/site/jacoco/jacoco.xml
```

Ese archivo XML es el que **lee SonarQube**. El scanner de Maven lo busca en esa ruta por
convención, así que no hace falta configurar la ruta a mano. JaCoCo también genera HTML y CSV
en la misma carpeta; son para consumo humano y Sonar los ignora.

Todo `target/` está en `.gitignore`: el reporte se regenera en cada build y nunca se versiona.

Por ahora **no hay un umbral mínimo de cobertura que rompa el build**. Primero se necesita
conocer la cobertura real y subirla escribiendo pruebas; poner el umbral antes solo incentivaría
excluir clases para maquillar el número. Tampoco se excluyen del análisis controladores,
servicios, repositorios, DTO ni entidades.

## Secretos

El análisis se autentica con un token de SonarQube Cloud expuesto como `SONAR_TOKEN`.

- Vive **solo** en GitHub Secrets (Settings → Secrets and variables → Actions).
- El workflow lo consume únicamente como `${{ secrets.SONAR_TOKEN }}`.
- **Nunca** debe aparecer en el `pom.xml`, en el README, en un archivo `.properties`, en un
  comando pegado en un chat, ni en la salida de un log.

GitHub enmascara el valor en los logs, pero eso es una red de seguridad, no una excusa: un token
filtrado se rota inmediatamente desde SonarQube Cloud.

Si alguien necesita analizar desde su máquina, genera **su propio** token y lo pasa por variable
de entorno; no se comparte el token de CI.

## Cómo ejecutar localmente

Para generar el reporte de cobertura sin contactar a SonarQube:

```bash
./mvnw verify
```

Eso ejecuta la suite completa y deja el reporte en `target/site/jacoco/`. Abrir
`target/site/jacoco/index.html` en el navegador muestra la cobertura clase por clase.

La suite completa **requiere PostgreSQL** en ejecución con las credenciales configuradas (ver el
README). Si no hay base disponible, se puede generar el reporte solo con las pruebas unitarias:

```bash
./mvnw -Dtest=EmpresaServiceTest,RegistroEmpresaDtoTest verify
```

El número que sale de ahí es menor que el real, porque deja fuera la prueba de contexto.

Ejecutar el análisis de Sonar desde una máquina local no es el flujo recomendado y exige manejar
un token propio en el entorno. El camino normal es abrir un pull request y dejar que CI analice.

## Cómo demostrarlo al profesor

1. Abrir la pestaña **Actions** del repositorio en GitHub.
2. Mostrar una ejecución del workflow **SonarQube Cloud** en verde, y dentro de ella el paso
   `Build, test and analyze` con los tests ejecutados y el análisis subido.
3. Abrir el proyecto en **SonarQube Cloud** (organización `kerosene21`).
4. Mostrar el **Quality Gate** y si está en `Passed` o `Failed`.
5. Mostrar **Security** (vulnerabilidades y security hotspots).
6. Mostrar **Reliability** (bugs).
7. Mostrar **Maintainability** (code smells y deuda técnica).
8. Mostrar **Coverage**, y explicar que ese número viene del `jacoco.xml` producido por las
   pruebas del propio build.
9. Mostrar **Duplications** (porcentaje de líneas duplicadas).

Punto clave a explicar: el análisis no es un botón que alguien aprieta cuando se acuerda, sino
una consecuencia automática de abrir un pull request.

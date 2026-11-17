import argparse
import fnmatch
import os
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field

PATRONES_SENSIBLES = (
    re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    re.compile(r"eyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\."),
    re.compile(r"gh[pousr]_[A-Za-z0-9]{36}"),
    re.compile(r"github_pat_\w{40,}", re.ASCII),
    re.compile(r"sq[apu]_[0-9a-f]{40}"),
)
TAMANO_MAXIMO_REVISADO = 64 * 1024 * 1024
CONTADORES_JACOCO = (("LINE", "Lineas"), ("BRANCH", "Ramas"), ("INSTRUCTION", "Instrucciones"), ("METHOD", "Metodos"))
MAXIMO_FALLIDAS = 20
CARACTERES_COMODIN = "*?["
DESTINOS_GITHUB = ("GITHUB_STEP_SUMMARY", "GITHUB_OUTPUT")
SALIDA_CORRECTA = 0
SALIDA_NO_PUBLICABLE = 1
SALIDA_RUTA_NO_PERMITIDA = 2
SALIDA_ERROR_ESCRITURA = 3


class RutaNoPermitida(ValueError):
    pass


@dataclass
class Rutas:
    salida: str
    junit: list
    jacoco: str | None
    revisar: list


@dataclass
class ResultadoJunit:
    rutas: list = field(default_factory=list)
    pruebas: int = 0
    fallos: int = 0
    errores: int = 0
    omitidas: int = 0
    tiempo: float = 0.0
    fallidas: list = field(default_factory=list)
    ilegibles: list = field(default_factory=list)

    @property
    def correctas(self):
        return self.pruebas - self.fallos - self.errores - self.omitidas


@dataclass
class Cobertura:
    contadores: dict = field(default_factory=dict)
    problema: str | None = None


def argumentos(valores=None):
    analizador = argparse.ArgumentParser(description="Resume informes JUnit y JaCoCo para GitHub Actions")
    analizador.add_argument("--titulo", required=True)
    analizador.add_argument("--junit", action="append", default=[])
    analizador.add_argument("--jacoco")
    analizador.add_argument("--sin-cobertura")
    analizador.add_argument("--revisar", action="append", default=[])
    analizador.add_argument("--salida", required=True)
    return analizador.parse_args(valores)


def resolver_dentro(ruta, base):
    try:
        base_real = os.path.realpath(base)
        real = os.path.realpath(os.path.join(base_real, ruta))
    except (TypeError, ValueError) as error:
        raise RutaNoPermitida(f"{ruta!r} no es una ruta valida") from error
    prefijo = base_real if base_real.endswith(os.sep) else base_real + os.sep
    if real != base_real and not real.startswith(prefijo):
        raise RutaNoPermitida(f"{ruta} queda fuera de {base_real}")
    return real


def exigir_extension(real, extension, opcion):
    if not real.endswith(extension):
        raise RutaNoPermitida(f"{opcion} debe indicar un archivo {extension}")
    return real


def validar_salida(ruta, base):
    real = exigir_extension(resolver_dentro(ruta, base), ".md", "--salida")
    if os.path.isdir(real):
        raise RutaNoPermitida("--salida no puede ser un directorio")
    return real


def validar_informe(ruta, base, opcion):
    return exigir_extension(resolver_dentro(ruta, base), ".xml", opcion)


def coincide(entrada, patron):
    oculto = entrada.startswith(".") and not patron.startswith(".")
    return not oculto and fnmatch.fnmatchcase(entrada, patron)


def coincidencias(patron, base):
    directorio, nombre = os.path.split(patron)
    if any(caracter in directorio for caracter in CARACTERES_COMODIN):
        raise RutaNoPermitida(f"{patron}: los comodines solo se admiten en el nombre del archivo")
    real = resolver_dentro(directorio or ".", base)
    if not os.path.isdir(real):
        return []
    entradas = sorted(entrada for entrada in os.listdir(real) if coincide(entrada, nombre))
    return [validar_informe(os.path.join(real, entrada), base, "--junit") for entrada in entradas]


def expandir_junit(patrones, base):
    rutas = set()
    for patron in patrones:
        rutas.update(coincidencias(patron, base))
    return sorted(rutas)


def validar_rutas(args, base):
    return Rutas(
        salida=validar_salida(args.salida, base),
        junit=expandir_junit(args.junit, base),
        jacoco=validar_informe(args.jacoco, base, "--jacoco") if args.jacoco else None,
        revisar=[resolver_dentro(directorio, base) for directorio in args.revisar],
    )


def destino_github(variable, entorno):
    ruta = entorno.get(variable)
    if not ruta or entorno.get("GITHUB_ACTIONS") != "true":
        return None
    temporal = entorno.get("RUNNER_TEMP")
    if not temporal:
        raise RutaNoPermitida(f"{variable} solo se acepta si RUNNER_TEMP esta definido")
    return resolver_dentro(ruta, temporal)


def destinos_github(entorno):
    return {variable: destino_github(variable, entorno) for variable in DESTINOS_GITHUB}


def relativa(ruta, base):
    return os.path.relpath(ruta, os.path.realpath(base))


def contiene_credencial(linea):
    return any(patron.search(linea) for patron in PATRONES_SENSIBLES)


def lineas_sensibles(ruta):
    with open(ruta, encoding="utf-8", errors="ignore") as archivo:
        return [numero for numero, linea in enumerate(archivo, start=1) if contiene_credencial(linea)]


def revisar_archivo(real, nombre):
    if os.path.getsize(real) > TAMANO_MAXIMO_REVISADO:
        return [f"{nombre}: supera el tamano revisable"]
    try:
        numeros = lineas_sensibles(real)
    except OSError:
        return [f"{nombre}: no se pudo leer para revisarlo"]
    return [f"{nombre}:{numero}" for numero in numeros]


def revisar_entrada(ruta, directorio, base):
    nombre = relativa(ruta, base)
    try:
        real = resolver_dentro(ruta, directorio)
    except RutaNoPermitida:
        return [f"{nombre}: enlace simbolico fuera de {relativa(directorio, base)}"]
    if os.path.isdir(real):
        return []
    return revisar_archivo(real, nombre)


def revisar_directorio(directorio, base):
    hallazgos = []
    for carpeta, subcarpetas, archivos in os.walk(directorio):
        for entrada in sorted(subcarpetas + archivos):
            hallazgos += revisar_entrada(os.path.join(carpeta, entrada), directorio, base)
    return hallazgos


def buscar_sensibles(directorios, base):
    hallazgos = []
    for directorio in directorios:
        if os.path.isdir(directorio):
            hallazgos += revisar_directorio(directorio, base)
    return hallazgos


def entero(elemento, atributo):
    try:
        return int(float(elemento.get(atributo, "0")))
    except ValueError:
        return 0


def decimal(elemento, atributo):
    try:
        return float(elemento.get(atributo, "0"))
    except ValueError:
        return 0.0


def suites(raiz_xml):
    return [raiz_xml] if raiz_xml.tag == "testsuite" else raiz_xml.findall("testsuite")


def caso_fallido(caso):
    return caso.find("failure") is not None or caso.find("error") is not None


def nombre_caso(caso):
    return f"{caso.get('classname', '')} > {caso.get('name', '')}".replace("\n", " ")


def acumular(resultado, suite):
    resultado.pruebas += entero(suite, "tests")
    resultado.fallos += entero(suite, "failures")
    resultado.errores += entero(suite, "errors")
    resultado.omitidas += entero(suite, "skipped")
    resultado.tiempo += decimal(suite, "time")
    resultado.fallidas += [nombre_caso(caso) for caso in suite.iter("testcase") if caso_fallido(caso)]


def leer_junit(rutas, base):
    resultado = ResultadoJunit(rutas=[relativa(ruta, base) for ruta in rutas])
    for ruta in rutas:
        try:
            raiz_xml = ET.parse(ruta).getroot()
        except (ET.ParseError, OSError):
            resultado.ilegibles.append(relativa(ruta, base))
            continue
        for suite in suites(raiz_xml):
            acumular(resultado, suite)
    return resultado


def leer_cobertura(ruta):
    if ruta is None:
        return None
    if not os.path.isfile(ruta):
        return Cobertura(problema="No se genero el informe de cobertura JaCoCo: Maven no llego a la fase verify.")
    try:
        raiz_xml = ET.parse(ruta).getroot()
    except (ET.ParseError, OSError):
        return Cobertura(problema="No se pudo leer el informe de cobertura JaCoCo.")
    contadores = {}
    for contador in raiz_xml.findall("counter"):
        cubierto = entero(contador, "covered")
        contadores[contador.get("type")] = (cubierto, cubierto + entero(contador, "missed"))
    return Cobertura(contadores=contadores)


def bloque_fallidas(fallidas):
    if not fallidas:
        return []
    lineas = ["", f"Pruebas fallidas ({len(fallidas)}):"]
    lineas += [f"- {nombre}" for nombre in fallidas[:MAXIMO_FALLIDAS]]
    if len(fallidas) > MAXIMO_FALLIDAS:
        lineas.append(f"- y {len(fallidas) - MAXIMO_FALLIDAS} mas en los informes descargables")
    return lineas


def bloque_junit(junit):
    if not junit.rutas:
        return ["No se encontraron informes JUnit: las pruebas no llegaron a ejecutarse o fallaron antes de generarlos."]
    lineas = [
        "| Pruebas | Correctas | Fallidas | Con error | Omitidas | Duracion |",
        "| ---: | ---: | ---: | ---: | ---: | ---: |",
        f"| {junit.pruebas} | {junit.correctas} | {junit.fallos} | {junit.errores} | {junit.omitidas} | {junit.tiempo:.1f} s |",
        "",
        f"Informes JUnit leidos: {len(junit.rutas) - len(junit.ilegibles)} de {len(junit.rutas)}.",
    ]
    if junit.ilegibles:
        lineas.append(f"Informes JUnit que no se pudieron leer: {', '.join(junit.ilegibles)}.")
    return lineas + bloque_fallidas(junit.fallidas)


def fila_cobertura(nombre, cubierto, total):
    porcentaje = f"{100 * cubierto / total:.1f} %" if total else "sin datos"
    return f"| {nombre} | {cubierto} | {total} | {porcentaje} |"


def bloque_cobertura(cobertura):
    if cobertura is None:
        return []
    if cobertura.problema:
        return [cobertura.problema]
    lineas = ["| Cobertura JaCoCo | Cubierto | Total | Porcentaje |", "| --- | ---: | ---: | ---: |"]
    lineas += [fila_cobertura(nombre, *cobertura.contadores[tipo]) for tipo, nombre in CONTADORES_JACOCO if tipo in cobertura.contadores]
    return lineas


def bloque_hallazgos(hallazgos):
    if not hallazgos:
        return []
    return ["", "Los informes contienen posibles credenciales y no se publican como artefacto:"] + [f"- {hallazgo}" for hallazgo in hallazgos]


def generar_markdown(titulo, junit, cobertura, sin_cobertura, hallazgos):
    lineas = [f"### {titulo}", ""] + bloque_junit(junit) + [""] + bloque_cobertura(cobertura)
    if sin_cobertura:
        lineas.append(f"Cobertura: {sin_cobertura}.")
    lineas += bloque_hallazgos(hallazgos)
    lineas.append("")
    return "\n".join(lineas)


def escribir(ruta, texto, modo="w"):
    with open(ruta, modo, encoding="utf-8") as archivo:
        archivo.write(texto)


def publicar(texto, salida, destinos, publicable):
    os.makedirs(os.path.dirname(salida), exist_ok=True)
    escribir(salida, texto)
    if destinos["GITHUB_STEP_SUMMARY"]:
        escribir(destinos["GITHUB_STEP_SUMMARY"], texto + "\n", "a")
    if destinos["GITHUB_OUTPUT"]:
        escribir(destinos["GITHUB_OUTPUT"], f"publicable={'true' if publicable else 'false'}\n", "a")


def principal(valores=None, entorno=None, base=None):
    args = argumentos(valores)
    entorno = os.environ if entorno is None else entorno
    base = os.getcwd() if base is None else base
    try:
        rutas = validar_rutas(args, base)
        destinos = destinos_github(entorno)
    except RutaNoPermitida as error:
        print(f"::error title=Ruta no permitida::{error}")
        return SALIDA_RUTA_NO_PERMITIDA
    hallazgos = buscar_sensibles(rutas.revisar, base)
    texto = generar_markdown(args.titulo, leer_junit(rutas.junit, base), leer_cobertura(rutas.jacoco), args.sin_cobertura, hallazgos)
    try:
        publicar(texto, rutas.salida, destinos, not hallazgos)
    except OSError as error:
        print(f"::error title=No se pudo escribir el resumen::{error}")
        return SALIDA_ERROR_ESCRITURA
    print(texto)
    for hallazgo in hallazgos:
        print(f"::error title=Informe no publicable::{hallazgo}")
    return SALIDA_NO_PUBLICABLE if hallazgos else SALIDA_CORRECTA


if __name__ == "__main__":
    sys.exit(principal())

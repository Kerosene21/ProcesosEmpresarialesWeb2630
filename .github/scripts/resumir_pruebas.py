import argparse
import glob
import os
import re
import sys
import xml.etree.ElementTree as ET

PATRONES_SENSIBLES = [
    re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----"),
    re.compile(r"eyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\."),
    re.compile(r"gh[pousr]_[A-Za-z0-9]{36}"),
    re.compile(r"github_pat_[A-Za-z0-9_]{40,}"),
    re.compile(r"sq[apu]_[0-9a-f]{40}"),
]
TAMANO_MAXIMO_REVISADO = 64 * 1024 * 1024
CONTADORES_JACOCO = [("LINE", "Lineas"), ("BRANCH", "Ramas"), ("INSTRUCTION", "Instrucciones"), ("METHOD", "Metodos")]
MAXIMO_FALLIDAS = 20


def argumentos():
    analizador = argparse.ArgumentParser(description="Resume informes JUnit y JaCoCo para GitHub Actions")
    analizador.add_argument("--titulo", required=True)
    analizador.add_argument("--junit", action="append", default=[])
    analizador.add_argument("--jacoco")
    analizador.add_argument("--sin-cobertura")
    analizador.add_argument("--revisar", action="append", default=[])
    analizador.add_argument("--salida", required=True)
    return analizador.parse_args()


def buscar_sensibles(directorios):
    hallazgos = []
    for directorio in directorios:
        if not os.path.isdir(directorio):
            continue
        for carpeta, _, archivos in os.walk(directorio):
            for nombre in sorted(archivos):
                ruta = os.path.join(carpeta, nombre)
                if os.path.getsize(ruta) > TAMANO_MAXIMO_REVISADO:
                    hallazgos.append(f"{ruta}: supera el tamano revisable")
                    continue
                with open(ruta, encoding="utf-8", errors="ignore") as archivo:
                    for numero, linea in enumerate(archivo, start=1):
                        if any(patron.search(linea) for patron in PATRONES_SENSIBLES):
                            hallazgos.append(f"{ruta}:{numero}")
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


def leer_junit(patrones):
    rutas = sorted({ruta for patron in patrones for ruta in glob.glob(patron)})
    totales = {"pruebas": 0, "fallos": 0, "errores": 0, "omitidas": 0, "tiempo": 0.0}
    fallidas = []
    ilegibles = []
    for ruta in rutas:
        try:
            raiz = ET.parse(ruta).getroot()
        except ET.ParseError:
            ilegibles.append(ruta)
            continue
        suites = [raiz] if raiz.tag == "testsuite" else raiz.findall("testsuite")
        for suite in suites:
            totales["pruebas"] += entero(suite, "tests")
            totales["fallos"] += entero(suite, "failures")
            totales["errores"] += entero(suite, "errors")
            totales["omitidas"] += entero(suite, "skipped")
            totales["tiempo"] += decimal(suite, "time")
            for caso in suite.iter("testcase"):
                if caso.find("failure") is not None or caso.find("error") is not None:
                    fallidas.append(f"{caso.get('classname', '')} > {caso.get('name', '')}".replace("\n", " "))
    return rutas, totales, fallidas, ilegibles


def leer_jacoco(ruta):
    raiz = ET.parse(ruta).getroot()
    contadores = {}
    for contador in raiz.findall("counter"):
        cubierto = entero(contador, "covered")
        total = cubierto + entero(contador, "missed")
        contadores[contador.get("type")] = (cubierto, total)
    return contadores


def resumen(args, hallazgos):
    lineas = [f"### {args.titulo}", ""]
    rutas, totales, fallidas, ilegibles = leer_junit(args.junit)
    if rutas:
        correctas = totales["pruebas"] - totales["fallos"] - totales["errores"] - totales["omitidas"]
        lineas += [
            "| Pruebas | Correctas | Fallidas | Con error | Omitidas | Duracion |",
            "| ---: | ---: | ---: | ---: | ---: | ---: |",
            f"| {totales['pruebas']} | {correctas} | {totales['fallos']} | {totales['errores']} | {totales['omitidas']} | {totales['tiempo']:.1f} s |",
            "",
            f"Informes JUnit leidos: {len(rutas) - len(ilegibles)} de {len(rutas)}.",
        ]
        if ilegibles:
            lineas.append(f"Informes JUnit que no se pudieron leer: {', '.join(ilegibles)}.")
        if fallidas:
            lineas += ["", f"Pruebas fallidas ({len(fallidas)}):"]
            lineas += [f"- {nombre}" for nombre in fallidas[:MAXIMO_FALLIDAS]]
            if len(fallidas) > MAXIMO_FALLIDAS:
                lineas.append(f"- y {len(fallidas) - MAXIMO_FALLIDAS} mas en los informes descargables")
    else:
        lineas.append("No se encontraron informes JUnit: las pruebas no llegaron a ejecutarse o fallaron antes de generarlos.")
    lineas.append("")
    if args.jacoco:
        if os.path.isfile(args.jacoco):
            contadores = leer_jacoco(args.jacoco)
            lineas += ["| Cobertura JaCoCo | Cubierto | Total | Porcentaje |", "| --- | ---: | ---: | ---: |"]
            for tipo, nombre in CONTADORES_JACOCO:
                if tipo in contadores:
                    cubierto, total = contadores[tipo]
                    porcentaje = f"{100 * cubierto / total:.1f} %" if total else "sin datos"
                    lineas.append(f"| {nombre} | {cubierto} | {total} | {porcentaje} |")
        else:
            lineas.append("No se genero el informe de cobertura JaCoCo: Maven no llego a la fase verify.")
    if args.sin_cobertura:
        lineas.append(f"Cobertura: {args.sin_cobertura}.")
    if hallazgos:
        lineas += ["", "Los informes contienen posibles credenciales y no se publican como artefacto:"]
        lineas += [f"- {hallazgo}" for hallazgo in hallazgos]
    lineas.append("")
    return "\n".join(lineas)


def escribir(ruta, texto, modo="w"):
    with open(ruta, modo, encoding="utf-8") as archivo:
        archivo.write(texto)


def principal():
    args = argumentos()
    hallazgos = buscar_sensibles(args.revisar)
    texto = resumen(args, hallazgos)
    carpeta = os.path.dirname(args.salida)
    if carpeta:
        os.makedirs(carpeta, exist_ok=True)
    escribir(args.salida, texto)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        escribir(os.environ["GITHUB_STEP_SUMMARY"], texto + "\n", "a")
    if os.environ.get("GITHUB_OUTPUT"):
        escribir(os.environ["GITHUB_OUTPUT"], f"publicable={'false' if hallazgos else 'true'}\n", "a")
    print(texto)
    for hallazgo in hallazgos:
        print(f"::error title=Informe no publicable::Posible credencial en {hallazgo}")
    return 1 if hallazgos else 0


if __name__ == "__main__":
    sys.exit(principal())

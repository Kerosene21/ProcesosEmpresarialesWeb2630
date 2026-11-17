import base64
import contextlib
import io
import json
import os
import re
import runpy
import sys
import tempfile
import unittest
from unittest import mock

import resumir_pruebas as rp

RUTA_SCRIPT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "resumir_pruebas.py")
INFORMES = "informes"
SALIDA_SIMPLE = "r.md"

SUREFIRE = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="co.ejemplo.CalculoTest" tests="4" failures="1" errors="1" skipped="1" time="1.5">
  <testcase classname="co.ejemplo.CalculoTest" name="suma" time="0.1"/>
  <testcase classname="co.ejemplo.CalculoTest" name="resta" time="0.1"><failure message="esperado 1">traza</failure></testcase>
  <testcase classname="co.ejemplo.CalculoTest" name="divide" time="0.1"><error message="division">traza</error></testcase>
  <testcase classname="co.ejemplo.CalculoTest" name="omitida" time="0"><skipped/></testcase>
</testsuite>
"""

VITEST = """<?xml version="1.0" encoding="UTF-8" ?>
<testsuites name="vitest tests" tests="3" failures="0" errors="0" time="0.5">
  <testsuite name="src/app/a.spec.ts" tests="2" failures="0" errors="0" skipped="1" time="0.3">
    <testcase classname="src/app/a.spec.ts" name="crea"/>
    <testcase classname="src/app/a.spec.ts" name="omite"><skipped/></testcase>
  </testsuite>
  <testsuite name="src/app/b.spec.ts" tests="1" failures="0" errors="0" skipped="0" time="0.2">
    <testcase classname="src/app/b.spec.ts" name="muestra"/>
  </testsuite>
</testsuites>
"""

JACOCO = (
    '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
    '<!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "report.dtd">'
    '<report name="demo">'
    '<counter type="INSTRUCTION" missed="10" covered="90"/>'
    '<counter type="BRANCH" missed="1" covered="3"/>'
    '<counter type="LINE" missed="2" covered="18"/>'
    '<counter type="METHOD" missed="0" covered="0"/>'
    '<counter type="CLASS" missed="0" covered="2"/>'
    "</report>"
)


def segmento(datos):
    return base64.urlsafe_b64encode(json.dumps(datos).encode()).decode().rstrip("=")


def jwt_falso():
    return ".".join([segmento({"alg": "HS256", "typ": "JWT"}), segmento({"sub": "prueba@ejemplo.com"}), "firma"])


SECRETOS_FALSOS = {
    "jwt": jwt_falso(),
    "clave privada": "-----BEGIN " + "RSA PRIVATE KEY-----",
    "token de GitHub": "gh" + "p_" + "A1b2C3" * 6,
    "token fino de GitHub": "github" + "_pat_" + "Ab_1" * 10,
    "token de Sonar": "sq" + "p_" + "0123456789abcdef" * 2 + "01234567",
}


class BaseTemporal(unittest.TestCase):
    def setUp(self):
        directorio = tempfile.TemporaryDirectory()
        self.addCleanup(directorio.cleanup)
        self.base = os.path.realpath(directorio.name)

    def ruta(self, *partes):
        return os.path.join(self.base, *partes)

    def crear(self, relativa, contenido=""):
        ruta = self.ruta(relativa)
        os.makedirs(os.path.dirname(ruta), exist_ok=True)
        with open(ruta, "w", encoding="utf-8") as archivo:
            archivo.write(contenido)
        return ruta

    def leer(self, ruta):
        with open(ruta, encoding="utf-8") as archivo:
            return archivo.read()

    def externo(self):
        directorio = tempfile.TemporaryDirectory()
        self.addCleanup(directorio.cleanup)
        return os.path.realpath(directorio.name)

    def enlazar(self, destino, relativa):
        enlace = self.ruta(relativa)
        os.makedirs(os.path.dirname(enlace), exist_ok=True)
        try:
            os.symlink(destino, enlace)
        except (OSError, NotImplementedError):
            self.skipTest("el sistema no permite crear enlaces simbolicos")
        return enlace

    def ejecutar(self, *argumentos, entorno=None):
        salida = io.StringIO()
        with contextlib.redirect_stdout(salida):
            codigo = rp.principal(list(argumentos), entorno if entorno is not None else {}, self.base)
        return codigo, salida.getvalue()

    def entorno_actions(self):
        temporal = self.ruta("runner")
        os.makedirs(temporal, exist_ok=True)
        return {
            "GITHUB_ACTIONS": "true",
            "RUNNER_TEMP": temporal,
            "GITHUB_STEP_SUMMARY": os.path.join(temporal, "step_summary"),
            "GITHUB_OUTPUT": os.path.join(temporal, "output"),
        }


class PruebasResolverRutas(BaseTemporal):
    def test_ruta_relativa_dentro_de_la_base(self):
        self.assertEqual(rp.resolver_dentro("target/informes/r.md", self.base), self.ruta("target", "informes", "r.md"))

    def test_ruta_absoluta_dentro_de_la_base(self):
        self.assertEqual(rp.resolver_dentro(self.ruta("a.md"), self.base), self.ruta("a.md"))

    def test_puntos_que_vuelven_a_la_base_se_aceptan(self):
        self.assertEqual(rp.resolver_dentro("target/../informes/r.md", self.base), self.ruta(INFORMES, "r.md"))

    def test_la_base_misma_se_acepta(self):
        self.assertEqual(rp.resolver_dentro(".", self.base), self.base)

    def test_puntos_que_salen_de_la_base_se_rechazan(self):
        with self.assertRaisesRegex(rp.RutaNoPermitida, "queda fuera"):
            rp.resolver_dentro("../fuera.md", self.base)

    def test_ruta_absoluta_externa_se_rechaza(self):
        with self.assertRaises(rp.RutaNoPermitida):
            rp.resolver_dentro(os.path.join(self.externo(), "r.md"), self.base)

    def test_prefijo_parecido_no_cuenta_como_dentro(self):
        with self.assertRaises(rp.RutaNoPermitida):
            rp.resolver_dentro(self.base + "-vecino" + os.sep + "r.md", self.base)

    def test_enlace_simbolico_que_sale_se_rechaza(self):
        self.enlazar(self.externo(), "enlace")
        with self.assertRaises(rp.RutaNoPermitida):
            rp.resolver_dentro("enlace/r.md", self.base)

    def test_enlace_simbolico_interno_se_acepta(self):
        os.makedirs(self.ruta("real"))
        self.enlazar(self.ruta("real"), "alias")
        self.assertEqual(rp.resolver_dentro("alias/r.md", self.base), self.ruta("real", "r.md"))

    def test_ruta_con_byte_nulo_se_rechaza(self):
        with self.assertRaisesRegex(rp.RutaNoPermitida, "no es una ruta valida"):
            rp.resolver_dentro("a\0b.md", self.base)

    def test_ruta_que_no_es_texto_se_rechaza(self):
        with self.assertRaises(rp.RutaNoPermitida):
            rp.resolver_dentro(None, self.base)

    def test_base_en_la_raiz_del_sistema(self):
        self.assertEqual(rp.resolver_dentro(self.ruta("x"), os.sep), self.ruta("x"))


class PruebasValidarRutas(BaseTemporal):
    def test_salida_debe_ser_markdown(self):
        with self.assertRaisesRegex(rp.RutaNoPermitida, "--salida"):
            rp.validar_salida("r.txt", self.base)

    def test_salida_no_puede_ser_directorio(self):
        os.makedirs(self.ruta("carpeta.md"))
        with self.assertRaisesRegex(rp.RutaNoPermitida, "directorio"):
            rp.validar_salida("carpeta.md", self.base)

    def test_salida_en_directorio_inexistente_se_acepta(self):
        self.assertEqual(rp.validar_salida("nuevo/sub/r.md", self.base), self.ruta("nuevo", "sub", "r.md"))

    def test_informe_debe_ser_xml(self):
        with self.assertRaisesRegex(rp.RutaNoPermitida, "--jacoco"):
            rp.validar_informe("informe.json", self.base, "--jacoco")

    def test_comodines_en_el_directorio_se_rechazan(self):
        with self.assertRaisesRegex(rp.RutaNoPermitida, "comodines"):
            rp.coincidencias("target/*/TEST-*.xml", self.base)

    def test_patron_fuera_de_la_base_se_rechaza(self):
        with self.assertRaises(rp.RutaNoPermitida):
            rp.coincidencias("../otro/TEST-*.xml", self.base)

    def test_patron_en_directorio_inexistente_no_devuelve_nada(self):
        self.assertEqual(rp.coincidencias("no-existe/TEST-*.xml", self.base), [])

    def test_patron_ignora_ocultos_y_distingue_mayusculas(self):
        for nombre in ("TEST-a.xml", ".TEST-b.xml", "test-c.xml"):
            self.crear(os.path.join(INFORMES, nombre), SUREFIRE)
        self.assertEqual(rp.coincidencias(INFORMES + "/TEST-*.xml", self.base), [self.ruta(INFORMES, "TEST-a.xml")])
        self.assertEqual(rp.coincidencias(INFORMES + "/.TEST-*.xml", self.base), [self.ruta(INFORMES, ".TEST-b.xml")])

    def test_patron_sin_directorio_usa_la_base(self):
        self.crear("TEST-raiz.xml", SUREFIRE)
        self.assertEqual(rp.coincidencias("TEST-*.xml", self.base), [self.ruta("TEST-raiz.xml")])

    def test_coincidencia_que_no_es_xml_se_rechaza(self):
        self.crear(INFORMES + "/TEST-a.txt")
        with self.assertRaisesRegex(rp.RutaNoPermitida, "--junit"):
            rp.coincidencias(INFORMES + "/TEST-*", self.base)

    def test_informe_enlazado_fuera_se_rechaza(self):
        externo = os.path.join(self.externo(), "otro.xml")
        with open(externo, "w", encoding="utf-8") as archivo:
            archivo.write(SUREFIRE)
        self.enlazar(externo, INFORMES + "/TEST-enlace.xml")
        with self.assertRaises(rp.RutaNoPermitida):
            rp.coincidencias(INFORMES + "/TEST-*.xml", self.base)

    def test_expandir_elimina_duplicados_y_ordena(self):
        self.crear(INFORMES + "/TEST-b.xml", SUREFIRE)
        self.crear(INFORMES + "/TEST-a.xml", SUREFIRE)
        rutas = rp.expandir_junit([INFORMES + "/TEST-*.xml", INFORMES + "/TEST-a.xml"], self.base)
        self.assertEqual(rutas, [self.ruta(INFORMES, "TEST-a.xml"), self.ruta(INFORMES, "TEST-b.xml")])

    def test_validar_rutas_completas(self):
        args = rp.argumentos(["--titulo", "T", "--salida", "s/r.md", "--jacoco", "j.xml", "--revisar", "informes"])
        rutas = rp.validar_rutas(args, self.base)
        self.assertEqual(rutas.salida, self.ruta("s", "r.md"))
        self.assertEqual(rutas.jacoco, self.ruta("j.xml"))
        self.assertEqual(rutas.revisar, [self.ruta(INFORMES)])
        self.assertEqual(rutas.junit, [])

    def test_validar_rutas_sin_jacoco(self):
        args = rp.argumentos(["--titulo", "T", "--salida", "r.md"])
        self.assertIsNone(rp.validar_rutas(args, self.base).jacoco)


class PruebasDestinosGithub(BaseTemporal):
    def test_fuera_de_actions_se_ignoran(self):
        entorno = {"GITHUB_STEP_SUMMARY": self.ruta("resumen"), "GITHUB_OUTPUT": self.ruta("salida")}
        self.assertEqual(rp.destinos_github(entorno), {"GITHUB_STEP_SUMMARY": None, "GITHUB_OUTPUT": None})

    def test_variable_vacia_se_ignora(self):
        entorno = self.entorno_actions()
        entorno["GITHUB_OUTPUT"] = ""
        self.assertIsNone(rp.destinos_github(entorno)["GITHUB_OUTPUT"])

    def test_dentro_de_runner_temp_se_aceptan(self):
        entorno = self.entorno_actions()
        destinos = rp.destinos_github(entorno)
        self.assertEqual(destinos["GITHUB_STEP_SUMMARY"], entorno["GITHUB_STEP_SUMMARY"])
        self.assertEqual(destinos["GITHUB_OUTPUT"], entorno["GITHUB_OUTPUT"])

    def test_fuera_de_runner_temp_se_rechaza(self):
        entorno = self.entorno_actions()
        entorno["GITHUB_OUTPUT"] = self.ruta("fuera-del-temporal")
        with self.assertRaisesRegex(rp.RutaNoPermitida, "queda fuera"):
            rp.destinos_github(entorno)

    def test_sin_runner_temp_se_rechaza(self):
        entorno = self.entorno_actions()
        del entorno["RUNNER_TEMP"]
        with self.assertRaisesRegex(rp.RutaNoPermitida, "RUNNER_TEMP"):
            rp.destinos_github(entorno)


class PruebasJunit(BaseTemporal):
    def test_surefire_con_fallos_errores_y_omitidas(self):
        resultado = rp.leer_junit([self.crear("TEST-a.xml", SUREFIRE)], self.base)
        self.assertEqual((resultado.pruebas, resultado.fallos, resultado.errores, resultado.omitidas), (4, 1, 1, 1))
        self.assertEqual(resultado.correctas, 1)
        self.assertAlmostEqual(resultado.tiempo, 1.5)
        self.assertEqual(resultado.fallidas, ["co.ejemplo.CalculoTest > resta", "co.ejemplo.CalculoTest > divide"])
        self.assertEqual(resultado.rutas, ["TEST-a.xml"])

    def test_vitest_con_varias_suites(self):
        resultado = rp.leer_junit([self.crear("vitest-junit.xml", VITEST)], self.base)
        self.assertEqual((resultado.pruebas, resultado.fallos, resultado.errores, resultado.omitidas), (3, 0, 0, 1))
        self.assertAlmostEqual(resultado.tiempo, 0.5)
        self.assertEqual(resultado.fallidas, [])

    def test_xml_corrupto_o_ilegible_se_registra(self):
        valido = self.crear("TEST-bien.xml", SUREFIRE)
        corrupto = self.crear("TEST-roto.xml", '<testsuite name="roto" tests="1"')
        os.makedirs(self.ruta("TEST-carpeta.xml"))
        resultado = rp.leer_junit([valido, corrupto, self.ruta("TEST-carpeta.xml")], self.base)
        self.assertEqual(resultado.ilegibles, ["TEST-roto.xml", "TEST-carpeta.xml"])
        self.assertEqual(resultado.pruebas, 4)

    def test_atributos_no_numericos_cuentan_cero(self):
        contenido = '<testsuite tests="muchos" failures="1.0" time="rapido"><testcase name="x"/></testsuite>'
        resultado = rp.leer_junit([self.crear("TEST-raro.xml", contenido)], self.base)
        self.assertEqual((resultado.pruebas, resultado.fallos, resultado.tiempo), (0, 1, 0.0))

    def test_nombre_de_caso_sin_saltos_de_linea(self):
        contenido = '<testsuite tests="1" failures="1"><testcase classname="C" name="linea&#10;dos"><failure/></testcase></testsuite>'
        resultado = rp.leer_junit([self.crear("TEST-salto.xml", contenido)], self.base)
        self.assertEqual(resultado.fallidas, ["C > linea dos"])

    def test_sin_informes(self):
        resultado = rp.leer_junit([], self.base)
        self.assertEqual((resultado.rutas, resultado.pruebas), ([], 0))


class PruebasCobertura(BaseTemporal):
    def test_lee_los_contadores(self):
        cobertura = rp.leer_cobertura(self.crear("jacoco.xml", JACOCO))
        self.assertIsNone(cobertura.problema)
        self.assertEqual(cobertura.contadores["LINE"], (18, 20))
        self.assertEqual(cobertura.contadores["METHOD"], (0, 0))

    def test_sin_ruta(self):
        self.assertIsNone(rp.leer_cobertura(None))

    def test_archivo_inexistente(self):
        self.assertIn("No se genero", rp.leer_cobertura(self.ruta("no.xml")).problema)

    def test_xml_corrupto(self):
        self.assertIn("No se pudo leer", rp.leer_cobertura(self.crear("jacoco.xml", "<report")).problema)

    def test_fila_sin_total(self):
        self.assertEqual(rp.fila_cobertura("Metodos", 0, 0), "| Metodos | 0 | 0 | sin datos |")


class PruebasCredenciales(BaseTemporal):
    def test_detecta_cada_tipo_de_credencial(self):
        for tipo, secreto in SECRETOS_FALSOS.items():
            with self.subTest(tipo=tipo):
                self.assertTrue(rp.contiene_credencial(f"antes {secreto} despues"))

    def test_texto_normal_no_es_credencial(self):
        for texto in ("SELECT u1_0.clave_hash FROM usuario", "eyJcorto.eyJcorto.", "github_pat_corto", "ghp_corto"):
            with self.subTest(texto=texto):
                self.assertFalse(rp.contiene_credencial(texto))

    def test_patron_de_github_pat_conserva_la_semantica_ascii(self):
        original = re.compile("github_pat_[" + "A-Za-z0-9_" + "]{40,}")
        actual = rp.PATRONES_SENSIBLES[3]
        caracteres = [chr(codigo) for codigo in range(128)] + ["é", "ñ", "Ω", "٣", "ｱ", "_", "²"]
        for caracter in caracteres:
            with self.subTest(caracter=repr(caracter)):
                texto = "github_pat_" + caracter * 40
                self.assertEqual(bool(original.search(texto)), bool(actual.search(texto)))

    def test_revision_informa_archivo_y_linea_sin_contenido(self):
        self.crear(INFORMES + "/log.txt", "normal\n" + SECRETOS_FALSOS["jwt"] + "\n")
        hallazgos = rp.buscar_sensibles([self.ruta(INFORMES)], self.base)
        self.assertEqual(hallazgos, [os.path.join(INFORMES, "log.txt") + ":2"])

    def test_revision_recorre_subdirectorios(self):
        self.crear(INFORMES + "/sub/clave.txt", SECRETOS_FALSOS["clave privada"])
        self.assertEqual(rp.buscar_sensibles([self.ruta(INFORMES)], self.base), [os.path.join(INFORMES, "sub", "clave.txt") + ":1"])

    def test_enlace_de_archivo_que_sale_no_es_publicable(self):
        externo = os.path.join(self.externo(), "secreto.txt")
        with open(externo, "w", encoding="utf-8") as archivo:
            archivo.write("valor")
        self.enlazar(externo, INFORMES + "/fuga.txt")
        hallazgos = rp.buscar_sensibles([self.ruta(INFORMES)], self.base)
        self.assertEqual(hallazgos, [os.path.join(INFORMES, "fuga.txt") + ": enlace simbolico fuera de informes"])

    def test_enlace_a_otro_archivo_del_repositorio_no_es_publicable(self):
        self.crear("configuracion.local", "valor")
        self.enlazar(self.ruta("configuracion.local"), INFORMES + "/copia.txt")
        hallazgos = rp.buscar_sensibles([self.ruta(INFORMES)], self.base)
        self.assertEqual(len(hallazgos), 1)
        self.assertIn("enlace simbolico fuera de informes", hallazgos[0])

    def test_enlace_de_directorio_que_sale_no_es_publicable(self):
        self.enlazar(self.externo(), INFORMES + "/externo")
        hallazgos = rp.buscar_sensibles([self.ruta(INFORMES)], self.base)
        self.assertEqual(hallazgos, [os.path.join(INFORMES, "externo") + ": enlace simbolico fuera de informes"])

    def test_enlace_interno_se_acepta(self):
        self.crear(INFORMES + "/sub/normal.txt", "sin datos sensibles")
        self.enlazar(self.ruta(INFORMES, "sub"), INFORMES + "/alias")
        self.assertEqual(rp.buscar_sensibles([self.ruta(INFORMES)], self.base), [])

    def test_archivo_demasiado_grande_no_es_publicable(self):
        self.crear(INFORMES + "/grande.txt", "x" * 10)
        with mock.patch.object(rp, "TAMANO_MAXIMO_REVISADO", 5):
            hallazgos = rp.buscar_sensibles([self.ruta(INFORMES)], self.base)
        self.assertEqual(hallazgos, [os.path.join(INFORMES, "grande.txt") + ": supera el tamano revisable"])

    def test_archivo_ilegible_no_es_publicable(self):
        self.crear(INFORMES + "/bloqueado.txt", "contenido")
        with mock.patch.object(rp, "lineas_sensibles", side_effect=PermissionError("denegado")):
            hallazgos = rp.buscar_sensibles([self.ruta(INFORMES)], self.base)
        self.assertEqual(hallazgos, [os.path.join(INFORMES, "bloqueado.txt") + ": no se pudo leer para revisarlo"])

    def test_directorio_inexistente_se_omite(self):
        self.assertEqual(rp.buscar_sensibles([self.ruta("no-existe")], self.base), [])


class PruebasMarkdown(BaseTemporal):
    def test_formato_completo_del_resumen(self):
        junit = rp.leer_junit([self.crear("TEST-a.xml", SUREFIRE)], self.base)
        cobertura = rp.leer_cobertura(self.crear("jacoco.xml", JACOCO))
        esperado = "\n".join([
            "### Backend",
            "",
            "| Pruebas | Correctas | Fallidas | Con error | Omitidas | Duracion |",
            "| ---: | ---: | ---: | ---: | ---: | ---: |",
            "| 4 | 1 | 1 | 1 | 1 | 1.5 s |",
            "",
            "Informes JUnit leidos: 1 de 1.",
            "",
            "Pruebas fallidas (2):",
            "- co.ejemplo.CalculoTest > resta",
            "- co.ejemplo.CalculoTest > divide",
            "",
            "| Cobertura JaCoCo | Cubierto | Total | Porcentaje |",
            "| --- | ---: | ---: | ---: |",
            "| Lineas | 18 | 20 | 90.0 % |",
            "| Ramas | 3 | 4 | 75.0 % |",
            "| Instrucciones | 90 | 100 | 90.0 % |",
            "| Metodos | 0 | 0 | sin datos |",
            "",
        ])
        self.assertEqual(rp.generar_markdown("Backend", junit, cobertura, None, []), esperado)

    def test_sin_informes_y_sin_cobertura(self):
        texto = rp.generar_markdown("Frontend", rp.ResultadoJunit(), None, "todavia no se mide", [])
        self.assertIn("No se encontraron informes JUnit", texto)
        self.assertTrue(texto.endswith("Cobertura: todavia no se mide.\n"))
        self.assertNotIn("Cobertura JaCoCo", texto)

    def test_problema_de_cobertura(self):
        texto = rp.generar_markdown("B", rp.ResultadoJunit(), rp.Cobertura(problema="No se pudo leer."), None, [])
        self.assertIn("\nNo se pudo leer.\n", texto)

    def test_recorta_mas_de_veinte_fallidas(self):
        lineas = rp.bloque_fallidas([f"caso {numero}" for numero in range(25)])
        self.assertEqual(lineas[1], "Pruebas fallidas (25):")
        self.assertEqual(len(lineas), 2 + rp.MAXIMO_FALLIDAS + 1)
        self.assertEqual(lineas[-1], "- y 5 mas en los informes descargables")

    def test_informes_ilegibles_en_el_resumen(self):
        junit = rp.ResultadoJunit(rutas=["a.xml", "b.xml"], ilegibles=["b.xml"])
        lineas = rp.bloque_junit(junit)
        self.assertIn("Informes JUnit leidos: 1 de 2.", lineas)
        self.assertIn("Informes JUnit que no se pudieron leer: b.xml.", lineas)

    def test_hallazgos_en_el_resumen(self):
        texto = rp.generar_markdown("B", rp.ResultadoJunit(), None, None, [INFORMES + "/log.txt:3"])
        self.assertIn("Los informes contienen posibles credenciales y no se publican como artefacto:\n- informes/log.txt:3\n", texto)


class PruebasPrincipal(BaseTemporal):
    def minimos(self, salida=SALIDA_SIMPLE, *extra):
        return ["--titulo", "T", "--salida", salida, *extra]

    def argumentos_backend(self, *extra):
        return ["--titulo", "Backend", "--junit", "target/surefire-reports/TEST-*.xml", "--jacoco", "target/site/jacoco/jacoco.xml",
                "--revisar", "target/surefire-reports", "--revisar", "target/site/jacoco", "--salida", "target/informes/resumen-backend.md", *extra]

    def preparar_informes(self, extra=""):
        self.crear("target/surefire-reports/TEST-a.xml", SUREFIRE)
        self.crear("target/surefire-reports/TEST-a.txt", "salida de la prueba\n" + extra)
        self.crear("target/site/jacoco/jacoco.xml", JACOCO)

    def test_ejecucion_correcta_publica_resumen_y_outputs(self):
        self.preparar_informes()
        entorno = self.entorno_actions()
        codigo, salida = self.ejecutar(*self.argumentos_backend(), entorno=entorno)
        resumen = self.leer(self.ruta("target", "informes", "resumen-backend.md"))
        self.assertEqual(codigo, rp.SALIDA_CORRECTA)
        self.assertEqual(self.leer(entorno["GITHUB_STEP_SUMMARY"]), resumen + "\n")
        self.assertEqual(self.leer(entorno["GITHUB_OUTPUT"]), "publicable=true\n")
        self.assertIn("| 4 | 1 | 1 | 1 | 1 | 1.5 s |", resumen)
        self.assertIn(resumen, salida)

    def test_fuera_de_actions_solo_escribe_la_salida(self):
        self.preparar_informes()
        codigo, _ = self.ejecutar(*self.argumentos_backend())
        self.assertEqual(codigo, rp.SALIDA_CORRECTA)
        self.assertTrue(os.path.isfile(self.ruta("target", "informes", "resumen-backend.md")))
        self.assertFalse(os.path.exists(self.ruta("runner")))

    def test_credenciales_devuelven_uno_sin_mostrarlas(self):
        secreto = SECRETOS_FALSOS["token de GitHub"]
        self.preparar_informes(extra=secreto)
        entorno = self.entorno_actions()
        codigo, salida = self.ejecutar(*self.argumentos_backend(), entorno=entorno)
        resumen = self.leer(self.ruta("target", "informes", "resumen-backend.md"))
        self.assertEqual(codigo, rp.SALIDA_NO_PUBLICABLE)
        self.assertEqual(self.leer(entorno["GITHUB_OUTPUT"]), "publicable=false\n")
        self.assertIn("::error title=Informe no publicable::", salida)
        self.assertNotIn(secreto, salida + resumen + self.leer(entorno["GITHUB_STEP_SUMMARY"]))

    def test_salida_fuera_devuelve_dos_sin_escribir(self):
        externo = self.externo()
        codigo, salida = self.ejecutar(*self.minimos(os.path.join(externo, SALIDA_SIMPLE)))
        self.assertEqual(codigo, rp.SALIDA_RUTA_NO_PERMITIDA)
        self.assertIn("::error title=Ruta no permitida::", salida)
        self.assertEqual(os.listdir(externo), [])

    def test_salida_con_puntos_devuelve_dos(self):
        codigo, _ = self.ejecutar(*self.minimos("../fuera.md"))
        self.assertEqual(codigo, rp.SALIDA_RUTA_NO_PERMITIDA)

    def test_junit_fuera_devuelve_dos(self):
        codigo, _ = self.ejecutar(*self.minimos(SALIDA_SIMPLE, "--junit", "../otro/TEST-*.xml"))
        self.assertEqual(codigo, rp.SALIDA_RUTA_NO_PERMITIDA)
        self.assertFalse(os.path.exists(self.ruta(SALIDA_SIMPLE)))

    def test_revisar_fuera_devuelve_dos(self):
        codigo, _ = self.ejecutar(*self.minimos(SALIDA_SIMPLE, "--revisar", self.externo()))
        self.assertEqual(codigo, rp.SALIDA_RUTA_NO_PERMITIDA)

    def test_destino_de_github_fuera_devuelve_dos_sin_escribir(self):
        entorno = self.entorno_actions()
        entorno["GITHUB_STEP_SUMMARY"] = os.path.join(self.externo(), "step_summary")
        codigo, _ = self.ejecutar(*self.minimos(), entorno=entorno)
        self.assertEqual(codigo, rp.SALIDA_RUTA_NO_PERMITIDA)
        self.assertFalse(os.path.exists(self.ruta(SALIDA_SIMPLE)))

    def test_error_al_crear_la_salida_devuelve_tres(self):
        self.crear("bloqueo", "no es un directorio")
        codigo, salida = self.ejecutar(*self.minimos("bloqueo/r.md"))
        self.assertEqual(codigo, rp.SALIDA_ERROR_ESCRITURA)
        self.assertIn("::error title=No se pudo escribir el resumen::", salida)

    def test_error_al_escribir_en_github_devuelve_tres(self):
        entorno = self.entorno_actions()
        os.makedirs(entorno["GITHUB_STEP_SUMMARY"])
        codigo, _ = self.ejecutar(*self.minimos(), entorno=entorno)
        self.assertEqual(codigo, rp.SALIDA_ERROR_ESCRITURA)

    def test_faltan_argumentos_obligatorios(self):
        with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as salida:
            rp.principal([], {}, self.base)
        self.assertEqual(salida.exception.code, 2)

    def test_valores_por_defecto_usan_el_directorio_actual(self):
        self.crear(INFORMES + "/vitest-junit.xml", VITEST)
        anterior = os.getcwd()
        os.chdir(self.base)
        self.addCleanup(os.chdir, anterior)
        argv = ["resumir_pruebas.py", "--titulo", "Frontend", "--junit", INFORMES + "/vitest-junit.xml", "--salida", INFORMES + "/r.md"]
        with mock.patch.dict(os.environ, {}, clear=True), mock.patch.object(sys, "argv", argv), contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(rp.principal(), rp.SALIDA_CORRECTA)
            with self.assertRaises(SystemExit) as salida:
                runpy.run_path(RUTA_SCRIPT, run_name="__main__")
        self.assertEqual(salida.exception.code, rp.SALIDA_CORRECTA)
        self.assertIn("| 3 | 2 | 0 | 0 | 1 | 0.5 s |", self.leer(self.ruta(INFORMES, "r.md")))


if __name__ == "__main__":
    unittest.main()

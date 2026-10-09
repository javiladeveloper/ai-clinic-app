package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import pe.saniape.app.data.staff.CargaHistoriaHc
import pe.saniape.app.data.staff.DatosAntropometriaHc
import pe.saniape.app.data.staff.DatosNoLegiblesHc
import pe.saniape.app.data.staff.DatosAtencionesHc
import pe.saniape.app.data.staff.DatosConsultasHc
import pe.saniape.app.data.staff.DatosControlesPrenatalesHc
import pe.saniape.app.data.staff.DatosEncabezadoHc
import pe.saniape.app.data.staff.DatosFiliacionHc
import pe.saniape.app.data.staff.DatosFisioEvaluacionHc
import pe.saniape.app.data.staff.DatosFotosHc
import pe.saniape.app.data.staff.DatosOdontogramaHc
import pe.saniape.app.data.staff.DatosParesHc
import pe.saniape.app.data.staff.DatosPsicologiaHc
import pe.saniape.app.data.staff.DatosTratamientosHc
import pe.saniape.app.data.staff.HallazgoDentalHc
import pe.saniape.app.data.staff.dineroHc
import pe.saniape.app.data.staff.hallazgosParaOdontograma
import pe.saniape.app.data.staff.interpretarRespuestaHistoria
import pe.saniape.app.data.staff.medidasControlPrenatalHc
import pe.saniape.app.data.staff.numeroHc
import pe.saniape.app.data.staff.parametroPsicoHc
import pe.saniape.app.data.staff.parsearHistoriaClinica
import pe.saniape.app.data.staff.seriePesoHc
import pe.saniape.app.data.staff.solesHc
import pe.saniape.app.data.staff.textoHallazgoHc
import pe.saniape.app.data.staff.tieneDeciduosHc

/**
 * Parseo de `GET /api/staff/historia/{id}?formato=json` (contrato de la web,
 * `lib/historia-tipos.ts`). El ejemplo sigue la forma del contrato §2.
 */
class HistoriaClinicaParseoTest {

    private val ejemplo = """
{
  "version": 1,
  "formato": "especialidad",
  "formatoEspecialidadDisponible": true,
  "graficos": true,
  "generadoEn": "2026-10-09T03:12:00.000Z",
  "fechaGeneracion": "2026-10-08",
  "clinica": { "nombre": "DALU Fisioterapia", "logoUrl": null, "ipress": "Categoría I-1 · RENIPRESS 0001" },
  "paciente": { "id": "p1", "nombre": "Ana Pérez", "iniciales": "AP" },
  "rubros": ["fisioterapia", "odontologia", "psicologia", "nutricion", "estetica"],
  "permisos": { "verContacto": true, "verPagos": true, "soloLoMio": false },
  "bloques": [
    { "id": "general", "rubro": "general", "titulo": null, "secciones": [
      { "id": "encabezado", "tipo": "encabezado", "titulo": "Encabezado", "obligatoria": true,
        "datos": { "nombre": "Ana Pérez", "iniciales": "AP", "filas": [ { "etiqueta": "DNI", "valor": "12345678" }, { "etiqueta": "Edad", "valor": "34 años" } ] } },
      { "id": "filiacion", "tipo": "filiacion", "titulo": "Filiación", "obligatoria": true,
        "datos": { "numeroHC": "N° HC 12345678", "apertura": "2026-09-01", "ipress": null,
          "filas": [ { "etiqueta": "Domicilio", "valor": "" }, { "etiqueta": "Ocupación", "valor": "Docente" } ], "alergias": "Penicilina" } },
      { "id": "antecedentes", "tipo": "antecedentes", "titulo": "Antecedentes", "obligatoria": true,
        "datos": { "items": [ { "etiqueta": "Alergias", "valor": "Penicilina", "alerta": true }, { "etiqueta": "Antecedentes médicos", "valor": "HTA" } ] } },
      { "id": "futuro", "tipo": "seccion_del_futuro", "titulo": "Algo nuevo", "obligatoria": false, "datos": { "x": 1 } },
      { "id": "rota", "tipo": "tratamientos", "titulo": "Rota", "obligatoria": false, "datos": { "items": "no es una lista" } }
    ] },
    { "id": "odontologia", "rubro": "odontologia", "titulo": "🦷 Odontología", "secciones": [
      { "id": "atenciones:odontologia", "tipo": "atenciones", "titulo": "Atenciones", "obligatoria": true,
        "datos": { "items": [ {
          "id": "a1", "citaId": "c1", "fecha": "2026-09-30", "hora": "09:00", "titulo": "Consulta", "profesional": "Dr. Salazar",
          "dental": true, "triaje": null,
          "campos": [ { "etiqueta": "Motivo", "valor": "Dolor de muela" } ],
          "diagnosticos": [ { "descripcion": "Caries de la dentina", "codigo": "K02.1", "tipo": "D", "tipoNombre": "Definitivo" } ],
          "diagnosticoSinCodificar": null,
          "recetas": [ { "id": "r1", "numero": "N° 000012", "fecha": "2026-09-30", "anulada": false,
            "items": ["Ibuprofeno 400 mg tableta — 1 cada 8 h x 5 días"], "profesional": "Dr. Salazar", "citaId": "c1" } ],
          "consentimientos": [],
          "faltan": ["Examen clínico"],
          "firma": { "titulo": "Firma y sello del cirujano dentista", "nombre": "Dr. Salazar", "colegiatura": "COP 6789",
            "digital": { "firmante": "SALAZAR JUAN", "firmadoAt": "2026-09-30T15:00:00Z", "certificado": null } }
        } ] } },
      { "id": "odontograma", "tipo": "odontograma", "titulo": "Odontograma", "obligatoria": true,
        "datos": { "inicial": null,
          "actual": { "fecha": "2026-09-30", "hallazgos": [
            { "diente": "16", "diente_hasta": null, "superficies": ["M", "O"], "estado": "Pendiente", "hallazgo_id": "h1",
              "nombre": "Caries", "color": "#dc2626", "marca_ausente": false, "por_boca": false, "notas": null },
            { "diente": "18", "diente_hasta": null, "superficies": null, "estado": "Realizado", "hallazgo_id": "h2",
              "nombre": "Ausente", "color": "#1d4ed8", "marca_ausente": true, "por_boca": false, "notas": "Extraída" },
            { "diente": "13", "diente_hasta": "23", "superficies": null, "estado": "Pendiente", "hallazgo_id": "h3",
              "nombre": "Puente", "color": "#dc2626", "marca_ausente": false, "por_boca": false, "notas": null }
          ] },
          "firma": { "titulo": "Firma y sello", "nombre": null, "colegiatura": null, "digital": null } } }
    ] },
    { "id": "fisioterapia", "rubro": "fisioterapia", "titulo": "🏃 Fisioterapia", "secciones": [
      { "id": "tratamientos:fisioterapia", "tipo": "tratamientos", "titulo": "Plan de rehabilitación", "obligatoria": false,
        "datos": { "items": [ {
          "id": "t1", "rubro": "fisioterapia", "nombre": "Rehabilitación de hombro", "modalidad": "Paquete", "estado": "Activo",
          "estadoPago": "Pendiente", "profesional": "Lic. Ruiz", "fechaInicio": "2026-09-02", "sesiones": "3/10",
          "precios": [ { "etiqueta": "Paquete", "valor": "S/ 500.00" } ], "diagnostico": "Tendinitis", "notas": null,
          "curvaEva": [ { "numero": 1, "fecha": "2026-09-02", "inicio": 7, "fin": 5 }, { "numero": 2, "fecha": "2026-09-05", "inicio": 5, "fin": null } ],
          "cuadro": { "comunes": ["Profesional: Lic. Ruiz · 45 min por sesión"], "verEvolucion": true, "verProfesional": false,
            "verDuracion": false, "verCosto": true,
            "filas": [ { "id": "s1", "numero": 1, "fecha": "2026-09-02", "hora": "10:00", "procedimientos": "TENS, ejercicios",
              "piezas": [], "motivo": null, "mejorias": "Menos dolor", "eva": "entra 7 → sale 5", "profesional": "Lic. Ruiz",
              "duracion": 45, "costo": 50 } ] },
          "pagos": { "filas": [ { "clave": "pago:1", "fecha": "2026-09-02", "metodo": "Yape", "nota": null, "monto": 200 } ],
            "acordado": 500, "pagado": 200, "saldo": 300 }
        } ] } },
      { "id": "consultas:fisioterapia", "tipo": "consultas", "titulo": "Consultas", "obligatoria": false,
        "datos": { "items": [ { "id": "c9", "fecha": "2026-09-01", "hora": "09:30", "tipo": "Evaluación", "profesional": "Lic. Ruiz",
          "estado": "Completada", "diagnostico": "Tendinitis", "notas": null } ] } },
      { "id": "fisio_evaluacion", "tipo": "fisio_evaluacion", "titulo": "Evaluación fisioterapéutica", "obligatoria": false,
        "datos": { "resumen": "Inicial 02/09/26 (Lic. Ruiz) · Reevaluación 30/09/26",
          "comparativo": [ { "clave": "flexion", "etiqueta": "Flexión de hombro", "nota": "(normal 180°)", "inicial": "90°",
            "fechaInicial": "2026-09-02", "ultima": "120°", "fechaUltima": "2026-09-30", "cambio": "+30°", "mejora": true } ],
          "hayUltima": true, "zonasInicial": { "hombro_der": 3 }, "zonasUltima": { "hombro_der": 1 },
          "zonasTexto": { "inicial": ["Hombro derecho"], "ultima": ["Hombro derecho"] },
          "objetivos": [ { "id": "o1", "texto": "Peinarse sin dolor", "logrado": true, "medida": "", "estado": "Logrado (2026-09-30)" } ],
          "objetivosLogrados": 1 } }
    ] },
    { "id": "psicologia", "rubro": "psicologia", "titulo": "🧠 Psicología", "secciones": [
      { "id": "psicologia", "tipo": "psicologia", "titulo": "Evaluación psicológica", "obligatoria": false,
        "datos": { "hayInforme": true, "hayEvaluaciones": true, "incluyeInforme": false, "incluyeTests": false, "evaluaciones": [] } }
    ] },
    { "id": "nutricion", "rubro": "nutricion", "titulo": "🥗 Nutrición", "secciones": [
      { "id": "antropometria", "tipo": "antropometria", "titulo": "Antropometría", "obligatoria": false,
        "datos": { "mediciones": [
          { "fecha": "2026-08-01", "peso": 80.4, "talla": 165, "imc": 29.5, "clasificacionImc": "Sobrepeso", "perimetroAbdominal": null,
            "cintura": 92, "cadera": null, "grasaPct": 31.2, "masaMuscular": null, "notas": null, "origen": "nutricion" },
          { "fecha": "2026-09-01", "peso": null, "talla": 165, "imc": null, "clasificacionImc": null, "perimetroAbdominal": null,
            "cintura": null, "cadera": null, "grasaPct": null, "masaMuscular": null, "notas": "Sin balanza", "origen": "triaje" },
          { "fecha": "2026-10-01", "peso": 77, "talla": 165, "imc": 28.3, "clasificacionImc": "Sobrepeso", "perimetroAbdominal": null,
            "cintura": 88, "cadera": null, "grasaPct": 29, "masaMuscular": null, "notas": null, "origen": "nutricion" }
        ], "cambioPeso": { "desde": 80.4, "hasta": 77, "diferencia": -3.4 } } },
      { "id": "controles:nutricion", "tipo": "controles", "titulo": "Controles", "obligatoria": false,
        "datos": { "items": [] } }
    ] },
    { "id": "estetica", "rubro": "estetica", "titulo": "✨ Estética", "secciones": [
      { "id": "campos_propios:estetica", "tipo": "campos_propios", "titulo": "Ficha estética", "obligatoria": false,
        "datos": { "items": [ { "etiqueta": "Fototipo (Fitzpatrick)", "valor": "III" } ] } },
      { "id": "fotos", "tipo": "fotos", "titulo": "Fotos", "obligatoria": false,
        "datos": { "fotos": [
          { "id": "f1", "url": "https://x/f1.jpg", "nombre": "f1.jpg", "momento": "Antes", "fecha": "2026-09-01", "notas": null, "tratamientoId": "t5" },
          { "id": "f2", "url": null, "nombre": "f2.jpg", "momento": "Despues", "fecha": "2026-10-01", "notas": null, "tratamientoId": "t5" }
        ], "pares": [ { "tratamientoId": "t5", "titulo": "Limpieza facial",
          "antes": { "id": "f1", "url": "https://x/f1.jpg", "nombre": "f1.jpg", "momento": "Antes", "fecha": "2026-09-01", "notas": null, "tratamientoId": "t5" },
          "despues": { "id": "f2", "url": null, "nombre": "f2.jpg", "momento": "Despues", "fecha": "2026-10-01", "notas": null, "tratamientoId": "t5" } } ] } }
    ] },
    { "id": "vacio", "rubro": "general", "titulo": "Solo cosas que no sé leer", "secciones": [
      { "id": "x", "tipo": "otro_tipo_nuevo", "titulo": "X", "obligatoria": false, "datos": {} }
    ] }
  ],
  "pie": { "izquierda": "DALU Fisioterapia", "derecha": "Historia generada el 8/10/2026 con Sania" }
}
"""

    @Test fun leeElSobreDelDocumento() {
        val doc = parsearHistoriaClinica(ejemplo)!!
        assertEquals(1, doc.version)
        assertTrue(doc.esPorEspecialidad)
        assertTrue(doc.formatoEspecialidadDisponible)
        assertTrue(doc.graficos)
        assertEquals("DALU Fisioterapia", doc.clinica.nombre)
        assertEquals("Ana Pérez", doc.paciente.nombre)
        assertTrue(doc.permisos.verPagos)
        assertFalse(doc.permisos.soloLoMio)
        assertEquals("Historia generada el 8/10/2026 con Sania", doc.pie.derecha)
    }

    @Test fun ignoraTiposDesconocidosYSeccionesRotasSinRomperLaHistoria() {
        val doc = parsearHistoriaClinica(ejemplo)!!
        val general = doc.bloques.first()
        assertEquals("general", general.id)
        assertNull(general.titulo)
        // encabezado, filiación, antecedentes; se saltan "seccion_del_futuro" y la rota.
        assertEquals(listOf("encabezado", "filiacion", "antecedentes"), general.secciones.map { it.tipo })
        // Un bloque que se queda sin secciones legibles no llega a la pantalla.
        assertTrue(doc.bloques.none { it.id == "vacio" })
        assertEquals(listOf("general", "odontologia", "fisioterapia", "psicologia", "nutricion", "estetica"), doc.bloques.map { it.id })
    }

    @Test fun generalEncabezadoFiliacionYAntecedentes() {
        val s = parsearHistoriaClinica(ejemplo)!!.bloques.first().secciones
        val enc = assertIs<DatosEncabezadoHc>(s[0].datos)
        assertEquals("AP", enc.iniciales)
        assertEquals("12345678", enc.filas.first { it.etiqueta == "DNI" }.valor)
        assertTrue(s[0].obligatoria)
        val fil = assertIs<DatosFiliacionHc>(s[1].datos)
        assertEquals("N° HC 12345678", fil.numeroHC)
        assertEquals("", fil.filas.first { it.etiqueta == "Domicilio" }.valor)   // raya para llenar
        assertEquals("Penicilina", fil.alergias)
        val ant = assertIs<DatosParesHc>(s[2].datos)
        assertTrue(ant.items[0].alerta)
        assertFalse(ant.items[1].alerta)   // `alerta` opcional → false
    }

    @Test fun atencionConDiagnosticoRecetaFaltantesYFirmaDigital() {
        val doc = parsearHistoriaClinica(ejemplo)!!
        val odo = doc.bloques.first { it.id == "odontologia" }
        assertEquals("🦷 Odontología", odo.titulo)
        val a = assertIs<DatosAtencionesHc>(odo.secciones[0].datos).items.single()
        assertTrue(a.dental)
        assertEquals("K02.1", a.diagnosticos.single().codigo)
        assertEquals("D", a.diagnosticos.single().tipo)
        assertEquals(1, a.recetas.single().items.size)
        assertEquals(listOf("Examen clínico"), a.faltan)
        assertEquals("COP 6789", a.firma.colegiatura)
        assertEquals("SALAZAR JUAN", a.firma.digital?.firmante)
    }

    @Test fun odontogramaAlFormatoDelComponenteNativo() {
        val doc = parsearHistoriaClinica(ejemplo)!!
        val od = assertIs<DatosOdontogramaHc>(doc.secciones.first { it.tipo == "odontograma" }.datos)
        assertNull(od.inicial)
        val hallazgos = od.actual.hallazgos
        assertEquals(3, hallazgos.size)
        assertEquals("23", hallazgos[2].dienteHasta)
        assertTrue(hallazgos[1].marcaAusente)
        val (registros, catalogo) = hallazgosParaOdontograma(hallazgos, "p1")
        assertEquals(3, registros.size)
        assertEquals(listOf("M", "O"), registros[0].superficies)
        assertEquals("Realizado", registros[1].estado)
        assertEquals("23", registros[2].dienteHasta)
        assertEquals(3, catalogo.size)
        assertTrue(catalogo.first { it.id == "h2" }.marcaAusente)
        assertFalse(tieneDeciduosHc(hallazgos))
        assertTrue(tieneDeciduosHc(listOf(HallazgoDentalHc(diente = "55"))))
        assertEquals("16 OM · Caries (Pendiente)", textoHallazgoHc(hallazgos[0]))
        assertEquals("13–23 · Puente (Pendiente)", textoHallazgoHc(hallazgos[2]))
    }

    @Test fun tratamientoFisioConCuadroCurvaEvaYPagos() {
        val doc = parsearHistoriaClinica(ejemplo)!!
        val fisio = doc.bloques.first { it.id == "fisioterapia" }
        assertEquals("Plan de rehabilitación", fisio.secciones[0].titulo)   // renombrado por la clínica
        val t = assertIs<DatosTratamientosHc>(fisio.secciones[0].datos).items.single()
        assertEquals("3/10", t.sesiones)
        assertEquals(2, t.curvaEva.size)
        assertNull(t.curvaEva[1].fin)
        assertTrue(t.cuadro.verCosto)
        assertEquals(50.0, t.cuadro.filas.single().costo)
        assertEquals("entra 7 → sale 5", t.cuadro.filas.single().eva)
        assertEquals(300.0, t.pagos?.saldo)
        val consultas = assertIs<DatosConsultasHc>(fisio.secciones[1].datos)
        assertEquals("Evaluación", consultas.items.single().tipo)
        val ev = assertIs<DatosFisioEvaluacionHc>(fisio.secciones[2].datos)
        assertEquals(3, ev.zonasInicial?.get("hombro_der"))
        assertEquals(true, ev.comparativo.single().mejora)
        assertEquals(1, ev.objetivosLogrados)
    }

    @Test fun psicologiaSinPedirNoTraeInformeNiPuntajes() {
        val doc = parsearHistoriaClinica(ejemplo)!!
        val p = assertIs<DatosPsicologiaHc>(doc.secciones.first { it.tipo == "psicologia" }.datos)
        assertTrue(p.hayInforme)
        assertTrue(p.hayEvaluaciones)
        assertFalse(p.incluyeInforme)
        assertTrue(p.evaluaciones.isEmpty())
        assertNull(parametroPsicoHc(informe = false, tests = false))
        assertEquals("informe", parametroPsicoHc(informe = true, tests = false))
        assertEquals("informe,tests", parametroPsicoHc(informe = true, tests = true))
        assertEquals("tests", parametroPsicoHc(informe = false, tests = true))
    }

    @Test fun psicologiaConInformeYTests() {
        val json = """{"version":1,"formato":"especialidad","bloques":[{"id":"psicologia","rubro":"psicologia","titulo":"🧠 Psicología","secciones":[
          {"id":"psicologia","tipo":"psicologia","titulo":"Evaluación psicológica","obligatoria":false,"datos":{
            "hayInforme":true,"hayEvaluaciones":true,"incluyeInforme":true,"incluyeTests":true,"evaluaciones":[{
              "evaluacionId":"e1","tratamientoId":"t9","titulo":"Evaluación psicológica — Evaluación infantil",
              "informe":{"id":"i1","version":2,"emitido":"2026-10-01","encabezado":"INFORME PSICOLÓGICO","reemplazo":null,
                "filiacion":[{"etiqueta":"Nombre","valor":"Ana"}],"secciones":[{"numero":1,"titulo":"Motivo","texto":"Dificultades de atención"}],
                "psicologo":"Ps. Lima","colegiatura":"CPsP 123","lugarFecha":"Tacna, 1 de octubre de 2026","pie":null},
              "tests":[{"id":"x1","nombre":"PHQ-9","fecha":"2026-09-20","edadTexto":null,"validez":null,"global":"Moderado",
                "puntajes":[{"escala":"Total","directo":"12","transformado":"","percentil":"","categoria":"Moderado"}]}]}]}}]}]}"""
        val doc = parsearHistoriaClinica(json)!!
        val p = assertIs<DatosPsicologiaHc>(doc.secciones.single().datos)
        val e = p.evaluaciones.single()
        assertEquals(2, e.informe?.version)
        assertEquals("Motivo", e.informe?.secciones?.single()?.titulo)
        assertEquals("12", e.tests.single().puntajes.single().directo)
        // Lo que no vino toma su valor por defecto.
        assertFalse(doc.graficos)
        assertEquals("", doc.clinica.nombre)
    }

    @Test fun nutricionSerieDePesoSoloConPeso() {
        val doc = parsearHistoriaClinica(ejemplo)!!
        val a = assertIs<DatosAntropometriaHc>(doc.secciones.first { it.tipo == "antropometria" }.datos)
        assertEquals(3, a.mediciones.size)
        val serie = seriePesoHc(a)
        assertEquals(listOf(80.4, 77.0), serie.map { it.valor })
        assertEquals(-3.4, a.cambioPeso?.diferencia)
        val controles = doc.secciones.first { it.tipo == "controles" }
        assertIs<DatosConsultasHc>(controles.datos)
    }

    @Test fun esteticaFichaYFotosAntesDespues() {
        val doc = parsearHistoriaClinica(ejemplo)!!
        val est = doc.bloques.first { it.id == "estetica" }
        assertEquals("campos_propios", est.secciones[0].tipo)
        assertEquals("III", assertIs<DatosParesHc>(est.secciones[0].datos).items.single().valor)
        val f = assertIs<DatosFotosHc>(est.secciones[1].datos)
        assertEquals(2, f.fotos.size)
        assertEquals("f1", f.pares.single().antes.id)
        assertNull(f.pares.single().despues.url)
    }

    @Test fun formatoEstandarUnSoloBloque() {
        val json = """{"version":1,"formato":"estandar","formatoEspecialidadDisponible":false,"graficos":false,
          "bloques":[{"id":"general","rubro":"general","titulo":null,"secciones":[
            {"id":"encabezado","tipo":"encabezado","titulo":"Encabezado","obligatoria":true,"datos":{"nombre":"Ana","iniciales":"A","filas":[]}},
            {"id":"tratamientos","tipo":"tratamientos","titulo":"Tratamientos","obligatoria":false,"datos":{"items":[
              {"id":"t1","rubro":"fisioterapia","nombre":"Rehab","sesiones":"1","curvaEva":[],"cuadro":{"filas":[]},"pagos":null,"precios":[]}]}}]}]}"""
        val doc = parsearHistoriaClinica(json)!!
        assertFalse(doc.esPorEspecialidad)
        assertFalse(doc.formatoEspecialidadDisponible)
        assertEquals(1, doc.bloques.size)
        val t = assertIs<DatosTratamientosHc>(doc.bloques.single().secciones[1].datos).items.single()
        assertNull(t.pagos)   // sin permiso de pagos
    }

    @Test fun webViejaQueRespondeHtmlCaeAlHtml() {
        val html = "<!doctype html><html lang=\"es\"><head><title>Historia Clínica</title></head><body>…</body></html>"
        assertEquals(CargaHistoriaHc.NoSoportado, interpretarRespuestaHistoria(200, html))
        // La página de "Función Premium" del servidor viejo también es HTML con 200.
        assertEquals(CargaHistoriaHc.NoSoportado, interpretarRespuestaHistoria(200, "\n  <!doctype html><p>🔒</p>"))
        assertNull(parsearHistoriaClinica(html))
        // Un JSON que no es una historia tampoco se dibuja.
        assertEquals(CargaHistoriaHc.NoSoportado, interpretarRespuestaHistoria(200, """{"ok":true}"""))
        // Ruta inexistente sin JSON.
        assertEquals(CargaHistoriaHc.NoSoportado, interpretarRespuestaHistoria(404, "Not Found"))
    }

    @Test fun versionMasNuevaQueLaAppCaeAlHtml() {
        assertEquals(CargaHistoriaHc.NoSoportado,
            interpretarRespuestaHistoria(200, """{"version":2,"formato":"estandar","bloques":[]}"""))
    }

    @Test fun respuestaOkYErroresDelContrato() {
        assertIs<CargaHistoriaHc.Ok>(interpretarRespuestaHistoria(200, ejemplo))
        val e403 = assertIs<CargaHistoriaHc.Error>(
            interpretarRespuestaHistoria(403, """{"error":"No tienes acceso a esta historia.","codigo":"SIN_ACCESO"}"""))
        assertEquals("SIN_ACCESO", e403.codigo)
        assertEquals("No tienes acceso a esta historia.", e403.mensaje)
        val e404 = assertIs<CargaHistoriaHc.Error>(interpretarRespuestaHistoria(404, """{"codigo":"NO_ENCONTRADO"}"""))
        assertEquals("No se encontró el paciente.", e404.mensaje)
        val e503 = assertIs<CargaHistoriaHc.Error>(interpretarRespuestaHistoria(503, "error"))
        assertNull(e503.codigo)
    }

    @Test fun seccionObligatoriaIlegibleNoSeDescartaEnSilencio() {
        val json = """{"version":1,"formato":"estandar","bloques":[{"id":"general","rubro":"general","titulo":null,"secciones":[
          {"id":"filiacion","tipo":"filiacion","titulo":"Filiación","obligatoria":true,"datos":{"filas":"roto"}},
          {"id":"norma-nueva","tipo":"tipo_de_norma_futuro","titulo":"Algo de la norma","obligatoria":true,"datos":{}},
          {"id":"opcional-rota","tipo":"tratamientos","titulo":"Tratamientos","obligatoria":false,"datos":{"items":"roto"}}]}]}"""
        val secciones = parsearHistoriaClinica(json)!!.secciones
        assertEquals(listOf("filiacion", "norma-nueva"), secciones.map { it.id })
        assertTrue(secciones.all { it.datos == DatosNoLegiblesHc && it.obligatoria })
        assertEquals("Filiación", secciones[0].titulo)
    }

    @Test fun regionalYMonedaPorTratamiento() {
        // Sin `regional` (servidor previo al multipaís): Perú, soles, DNI.
        val viejo = parsearHistoriaClinica(ejemplo)!!
        assertEquals("PEN", viejo.regional.moneda)
        assertEquals("DNI", viejo.regional.etiquetaDocumento)
        assertNull(assertIs<DatosTratamientosHc>(viejo.secciones.first { it.tipo == "tratamientos" }.datos).items.single().moneda)
        val json = """{"version":1,"formato":"estandar","regional":{"pais":"BO","moneda":"BOB","zona":"America/La_Paz","etiquetaDocumento":"CI"},
          "bloques":[{"id":"general","rubro":"general","titulo":null,"secciones":[
            {"id":"t","tipo":"tratamientos","titulo":"Tratamientos","obligatoria":false,"datos":{"items":[{"id":"t1","nombre":"Rehab","moneda":"BOB"}]}}]}]}"""
        val doc = parsearHistoriaClinica(json)!!
        assertEquals("BO", doc.regional.pais)
        assertEquals("CI", doc.regional.etiquetaDocumento)
        assertEquals("BOB", assertIs<DatosTratamientosHc>(doc.secciones.single().datos).items.single().moneda)
        assertEquals("Bs 1,234.50", dineroHc(1234.5, "BOB"))
        assertEquals("S/ 1,234.50", dineroHc(1234.5, "PEN"))
        assertEquals("S/ 0.50", dineroHc(0.5, null))
        assertEquals("S/ 1,000,000.00", dineroHc(1_000_000.0, "PEN"))
        assertEquals("-S/ 3.40", dineroHc(-3.4, "PEN"))
    }

    @Test fun formatosDeNumeros() {
        assertEquals("72", numeroHc(72.0))
        assertEquals("72.5", numeroHc(72.46))
        assertEquals("-3.4", numeroHc(-3.4))
        assertEquals("S/ 120.00", solesHc(120.0))
        assertEquals("S/ 0.50", solesHc(0.5))
    }

    @Test fun obstetriciaCuadroDeControlesPrenatales() {
        val json = """{"version":1,"formato":"especialidad","rubros":["obstetricia"],
          "bloques":[{"id":"obstetricia","rubro":"obstetricia","titulo":"🤰 Obstetricia","secciones":[
            {"id":"controles_prenatales:obstetricia","tipo":"controles_prenatales","titulo":"Controles prenatales","obligatoria":false,
             "datos":{"resumen":[{"etiqueta":"FUR","valor":"01/03/26"},{"etiqueta":"Controles registrados","valor":"2"}],
               "filas":[
                 {"id":"c1","numero":1,"fecha":"2026-05-10","hora":"09:00","edadGestacional":"10 sem","presionArterial":"100/60","peso":58,"alturaUterina":null,"lcf":null,"examen":"Útero grávido","indicaciones":"Ácido fólico","proximoControl":null,"profesional":"Obst. Carmen Vela"},
                 {"id":"c2","numero":2,"fecha":"2026-08-21","hora":null,"edadGestacional":"24 sem 5 d","presionArterial":"110/70","peso":63.5,"alturaUterina":24,"lcf":140,"examen":null,"indicaciones":null,"proximoControl":"2026-09-20","profesional":null}]}}]}]}"""
        val doc = parsearHistoriaClinica(json)!!
        assertEquals("obstetricia", doc.bloques.single().rubro)
        val d = assertIs<DatosControlesPrenatalesHc>(doc.secciones.single().datos)
        assertEquals(listOf("FUR", "Controles registrados"), d.resumen.map { it.etiqueta })
        assertEquals(2, d.filas.size)
        assertEquals("EG 10 sem · PA 100/60 · 58 kg", medidasControlPrenatalHc(d.filas[0]))
        assertEquals("EG 24 sem 5 d · PA 110/70 · 63.5 kg · AU 24 cm · LCF 140", medidasControlPrenatalHc(d.filas[1]))
        assertEquals("2026-09-20", d.filas[1].proximoControl)
    }
}

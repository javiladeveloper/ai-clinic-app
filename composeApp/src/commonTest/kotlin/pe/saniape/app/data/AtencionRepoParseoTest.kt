package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.staff.AtencionRepo
import pe.saniape.app.data.staff.BorradorAtencion
import pe.saniape.app.data.staff.DiagnosticoCie
import pe.saniape.app.data.staff.ExamenSolicitado
import pe.saniape.app.data.staff.requiereConsentimiento

/**
 * Parseo de `GET /api/staff/atencion/consulta` y armado del cuerpo de `guardar`.
 * El JSON es el ejemplo del contrato (docs/app-contrato-atencion.md §2 en la web).
 */
class AtencionRepoParseoTest {

    private val ejemplo = """
{
  "ok": true,
  "cita": {
    "id": "cita1", "fecha": "2026-09-30", "hora": "09:00:00", "tipo": "Consulta",
    "estado": "Confirmada", "no_asistio": false,
    "paciente_id": "p1", "terapeuta_id": "t1", "especialidad_id": "esp-medicina",
    "tratamiento_id": null, "procedimiento_id": null,
    "costo": 50, "pagada_at": null, "duracion": 20,
    "notas": "Motivo: dolor de cabeza", "sede_id": null, "diagnostico": null,
    "paciente": {
      "id": "p1", "nombre": "Ana Pérez", "dni": "12345678", "estado": "Activo",
      "fecha_nacimiento": "1990-01-01", "edad": null, "telefono": "987654321",
      "direccion": null, "ocupacion": null, "alergias": null, "antecedentes": null,
      "medicacion_actual": null, "...": "resto de columnas de pacientes",
      "historia": null
    },
    "terapeuta": { "id": "t1", "nombre": "Dr. Salazar" },
    "procedimiento": null,
    "tratamiento": null
  },
  "atencion": {
    "id": "a1", "cita_id": "cita1", "paciente_id": "p1", "terapeuta_id": "t1",
    "tratamiento_id": null, "fecha": "2026-09-30", "hora": "09:00:00",
    "llegada_at": "2026-09-30T14:10:40.000Z", "triaje_at": null, "triaje_por_nombre": null,
    "consulta_inicio_at": "2026-09-30T14:10:40.000Z", "atendida_at": null,
    "motivo_consulta": null, "tiempo_enfermedad": null, "relato": null, "funciones_biologicas": null,
    "presion_sistolica": null, "presion_diastolica": null, "frecuencia_cardiaca": null,
    "frecuencia_respiratoria": null, "temperatura": null, "saturacion_o2": null,
    "peso": null, "talla": null, "imc": null, "perimetro_abdominal": null,
    "examen_fisico": null, "diagnosticos": [], "examenes": [],
    "plan_trabajo": null, "tratamiento": null, "observaciones": null,
    "nota_procedimiento": null, "control_cita_id": null, "control": null,
    "created_at": "2026-09-30T14:10:40.000Z", "updated_at": "2026-09-30T14:10:40.000Z"
  },
  "recetas": [],
  "consentimientos": [],
  "indicados": [],
  "servicios": [
    { "id": "s1", "nombre": "Control", "precio": 30, "modo_cobro": null,
      "precio_unitario_sugerido": null, "especialidad_id": "esp-medicina",
      "tipo_cita": null, "categoria": null, "plantillas": [] }
  ],
  "profesionales": [
    { "id": "t1", "nombre": "Dr. Salazar", "cmp": "45678", "estado": "Activo", "puedePrescribir": true,
      "especialidades": [{ "id": "esp-medicina", "rubro": "medicina_general" }] }
  ],
  "frases": [{ "campo": "motivo", "texto": "Cefalea", "usos": 3 }],
  "motivoSugerido": "dolor de cabeza",
  "diagnosticosSugeridos": [],
  "flags": {
    "dental": false,
    "esProcedimiento": false,
    "recetasAplica": true,
    "especialidadId": "esp-medicina",
    "puedeAtender": true,
    "soloLectura": false,
    "completada": false,
    "cobrable": true,
    "edad": 36,
    "numeroHc": "",
    "estadoCola": "en_consulta",
    "pasos": [
      { "clave": "motivo", "titulo": "Motivo y anamnesis" },
      { "clave": "vitales", "titulo": "Funciones vitales" },
      { "clave": "examen", "titulo": "Examen físico" },
      { "clave": "diagnostico", "titulo": "Diagnóstico CIE-10" },
      { "clave": "plan", "titulo": "Plan" },
      { "clave": "cierre", "titulo": "Cierre" }
    ],
    "faltantesFiliacion": ["Sexo", "Domicilio", "Estado civil", "Grado de instrucción", "Ocupación", "Lugar de nacimiento"],
    "faltantesAtencion": ["Tiempo de enfermedad", "Funciones vitales", "Examen físico", "Diagnóstico", "Plan de trabajo / tratamiento"],
    "avisosCierre": [
      "Para una HC completa (NTS 139) falta: Tiempo de enfermedad · Funciones vitales · Examen físico · Diagnóstico · Plan de trabajo / tratamiento."
    ],
    "consentimientosEmitidos": 0
  },
  "modulos": { "camposTriaje": ["presion", "peso"], "recetasOptIn": false }
}
""".trimIndent()

    @Test fun parseaElEjemploDelContrato() {
        val d = AtencionRepo.parsearConsulta(ejemplo)
        assertEquals("cita1", d.cita.id)
        assertEquals("Ana Pérez", d.cita.paciente?.nombre)
        assertEquals(50.0, d.cita.costo)
        assertEquals(6, d.flags.pasos.size)
        assertEquals("motivo", d.flags.pasos.first().clave)
        assertEquals(emptyList(), d.atencion?.diagnosticos)
        assertEquals("a1", d.atencion?.id)
        assertEquals(1, d.servicios.size)
        assertEquals("45678", d.profesionales[0].cmp)
        assertTrue(d.profesionales[0].puedePrescribir)
        assertEquals(emptyList(), d.diagnosticosSugeridos)
        assertEquals("dolor de cabeza", d.motivoSugerido)
        assertEquals(listOf("presion", "peso"), d.modulos.camposTriaje)
        assertTrue(d.flags.puedeAtender)
        assertFalse(d.flags.soloLectura)
        assertEquals(36, d.flags.edad)
        assertNull(d.cita.tratamiento)
        assertFalse(requiereConsentimiento(d.cita))
    }

    @Test fun atencionNulaQuedaNula() {
        val sinAtencion = """
            { "ok": true, "cita": { "id": "c2", "tipo": "Sesión", "costo": 0,
                "tratamiento": { "id": "tr1", "diagnostico": "Gonartrosis", "cita_origen_id": "c1",
                  "estado_pago": "Pendiente", "precio_acordado": 300,
                  "procedimiento": { "id": "pr1", "nombre": "Infiltración de rodilla", "especialidad_id": "esp-trauma",
                    "plantillas": [
                      { "id": "pl-vieja", "procedimiento": "Infiltración", "activo": false },
                      { "id": "pl1", "procedimiento": "Infiltración de rodilla", "activo": true }
                    ] } } },
              "atencion": null, "motivoSugerido": "rodilla",
              "diagnosticosSugeridos": [{ "codigo": null, "descripcion": "Gonartrosis", "tipo": "P" }],
              "flags": { "esProcedimiento": true, "puedeAtender": false, "soloLectura": true } }
        """.trimIndent()
        val d = AtencionRepo.parsearConsulta(sinAtencion)
        assertNull(d.atencion)
        assertEquals("c2", d.cita.id)
        assertEquals("rodilla", d.motivoSugerido)
        assertEquals(DiagnosticoCie(null, "Gonartrosis", "P"), d.diagnosticosSugeridos.single())
        assertTrue(d.flags.soloLectura)
        // tratamiento.procedimiento.plantillas: una activa = requiere consentimiento.
        val proc = d.cita.tratamiento?.procedimiento
        assertEquals("pr1", proc?.id)
        assertEquals("Infiltración de rodilla", proc?.nombre)
        assertEquals(listOf("pl-vieja", "pl1"), proc?.plantillas?.map { it.id })
        assertEquals(listOf(false, true), proc?.plantillas?.map { it.activo })
        assertTrue(requiereConsentimiento(d.cita))
        // Solo plantillas inactivas = no lo requiere.
        val soloInactivas = d.cita.copy(tratamiento = d.cita.tratamiento?.copy(
            procedimiento = proc?.copy(plantillas = proc.plantillas.filter { !it.activo })))
        assertFalse(requiereConsentimiento(soloInactivas))
    }

    @Test fun cuerpoDeGuardarLlevaTodoYLosDefaults() {
        val b = BorradorAtencion(
            terapeutaId = "t1",
            textos = mapOf("motivo_consulta" to "Dolor de cabeza", "relato" to ""),
            vitales = mapOf("presion_sistolica" to "120", "temperatura" to "36,5", "peso" to " "),
            diagnosticos = listOf(DiagnosticoCie(codigo = "R51", descripcion = "Cefalea")),
            examenes = listOf(ExamenSolicitado(nombre = "Hemograma completo")),
        )
        val cuerpo = AtencionRepo.cuerpoGuardar("cita1", b)
        val texto = cuerpo.toString()
        assertEquals(JsonPrimitive("cita1"), cuerpo["citaId"])
        val br = cuerpo["borrador"]!!.jsonObject
        // encodeDefaults: el tipo por defecto "P" viaja.
        assertTrue(texto.contains("\"tipo\":\"P\""), texto)
        assertEquals(JsonPrimitive("t1"), br["terapeutaId"])
        assertEquals(JsonPrimitive("Dolor de cabeza"), br["motivo_consulta"])
        // Las 8 claves de vitales van siempre (vacío = null: guardar REEMPLAZA).
        for (k in listOf("presion_sistolica", "presion_diastolica", "frecuencia_cardiaca",
            "frecuencia_respiratoria", "temperatura", "saturacion_o2", "peso", "talla")) {
            assertTrue(br.containsKey(k), "falta $k")
        }
        assertEquals(JsonPrimitive("120"), br["presion_sistolica"])
        assertEquals(JsonPrimitive("36,5"), br["temperatura"])
        assertEquals(JsonNull, br["peso"])
        // Sin perímetro ni nota en el borrador: no se tocan (no se mandan).
        assertFalse(br.containsKey("perimetro_abdominal"))
        assertFalse(br.containsKey("nota_procedimiento"))
        // diagnosticos y examenes se mandan siempre.
        assertEquals(1, br["diagnosticos"]!!.jsonArray.size)
        assertEquals(1, br["examenes"]!!.jsonArray.size)
    }

    @Test fun cuerpoDeGuardarVacioIgualMandaListas() {
        val br = AtencionRepo.cuerpoGuardar("c", BorradorAtencion())["borrador"]!!.jsonObject
        assertEquals(JsonArray(emptyList()), br["diagnosticos"])
        assertEquals(JsonArray(emptyList()), br["examenes"])
        assertEquals(JsonNull, br["terapeutaId"])
    }

    @Test fun clasificaRespuestas() {
        val ok = AtencionRepo.resultadoDeRespuesta(200, """{"ok":true,"citaId":"x"}""")
        assertTrue(ok.registrada)
        assertNull(ok.rechazo)
        assertEquals(JsonPrimitive("x"), ok.cuerpo?.get("citaId"))

        val vacia = AtencionRepo.resultadoDeRespuesta(200, """{"ok":true,"atencion":null,"vacia":true}""")
        assertTrue(vacia.registrada)

        val invalida = AtencionRepo.resultadoDeRespuesta(422,
            """{"error":"Presión incompleta","errores":["Presión incompleta","Peso fuera de rango"],"codigo":"ATENCION_INVALIDA"}""")
        assertFalse(invalida.registrada)
        assertEquals("ATENCION_INVALIDA", invalida.codigo)
        assertEquals(422, invalida.rechazo?.status)
        assertTrue(invalida.rechazo!!.error.contains("Peso fuera de rango"))

        val html = AtencionRepo.resultadoDeRespuesta(502, "<html>Bad gateway</html>")
        assertFalse(html.registrada)
        assertEquals(502, html.rechazo?.status)
        assertNull(html.codigo)
        assertTrue(html.rechazo!!.error.isNotBlank())
    }

    @Test fun cie10SeLeeComoDiagnosticos() {
        val l = AtencionRepo.parsearCie10("""{"resultados":[{"codigo":"K02.1","descripcion":"Caries de la dentina"}]}""")
        assertEquals(listOf(DiagnosticoCie("K02.1", "Caries de la dentina", "P")), l)
    }

    @Test fun sugerenciasDeMedicamentos() {
        val l = AtencionRepo.parsearSugerencias("""{ "sugerencias": [
          { "dci": "Paracetamol", "concentracion": "500 mg", "forma": "Tableta", "via": "Oral", "usos": 12, "origen": "clinica" },
          { "dci": "Amoxicilina", "concentracion": "500 mg", "forma": "Cápsula", "usos": 0, "origen": "petitorio" }
        ] }""")
        assertEquals(2, l.size)
        assertEquals(12, l[0].usos)
        assertEquals("Oral", l[0].via)
        assertNull(l[1].via)
    }
}

package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Parseo de `GET /api/staff/pacientes-nuevos?mes=…` y la lógica pura de la pantalla. */
class PacientesNuevosParseoTest {

    private val respuesta = """
        {
          "periodo": { "mes": "2026-10", "desde": "2026-10-01", "hasta": "2026-10-31", "etiqueta": "octubre 2026" },
          "embudo": { "nuevos": 4, "evaluados": 3, "enTratamiento": 2, "pagaron": 1, "extra": 9 },
          "filas": [
            {
              "pacienteId": "p1", "nombre": "Ana Quispe", "telefono": "987654321",
              "fechaRegistro": "2026-10-02", "sedeId": null, "etapa": "pago",
              "evaluacion": { "fecha": "2026-10-03", "estado": "atendida", "profesional": "Lic. Luis" },
              "tratamiento": { "id": "t1", "servicio": "Fisioterapia", "modalidad": "Paquete", "totalSesiones": 10, "sesionesCompletadas": 4 },
              "pago": { "estado": "pagado", "deuda": null },
              "proximaCita": { "fecha": "2026-10-10", "hora": "09:00" }
            },
            {
              "pacienteId": "p2", "nombre": "Beto", "telefono": null,
              "fechaRegistro": "2026-10-05", "sedeId": "s1", "etapa": "en_tratamiento",
              "evaluacion": { "fecha": "2026-10-06", "estado": "atendida", "profesional": null },
              "tratamiento": { "id": "t2", "servicio": "Masaje", "modalidad": null, "totalSesiones": null, "sesionesCompletadas": 0 },
              "pago": { "estado": "parcial", "deuda": 120.5 },
              "proximaCita": null
            },
            {
              "pacienteId": "p3", "nombre": "Carla", "telefono": "912345678",
              "fechaRegistro": "2026-10-07", "sedeId": null, "etapa": "evaluado",
              "evaluacion": { "fecha": "2026-10-08", "estado": "agendada", "profesional": "Dra. Paz" },
              "tratamiento": null, "pago": null, "proximaCita": { "fecha": "2026-10-08", "hora": null }
            },
            {
              "pacienteId": "p4", "nombre": "Dante", "fechaRegistro": "2026-10-09",
              "etapa": "sin_evaluacion", "evaluacion": null, "tratamiento": null,
              "pago": { "estado": "sin_pagar", "deuda": null }, "proximaCita": null, "nuevoCampo": 1
            }
          ]
        }
    """.trimIndent()

    @Test
    fun leePeriodoYEmbudo() {
        val r = parsearPacientesNuevos(respuesta)
        assertEquals("2026-10", r.periodo.mes)
        assertEquals("octubre 2026", r.periodo.etiqueta)
        assertEquals(4, r.embudo.nuevos)
        assertEquals(3, r.embudo.evaluados)
        assertEquals(2, r.embudo.enTratamiento)
        assertEquals(1, r.embudo.pagaron)
    }

    @Test
    fun leeCadaFila() {
        val f = parsearPacientesNuevos(respuesta).filas
        assertEquals(4, f.size)
        val ana = f[0]
        assertEquals("p1", ana.pacienteId)
        assertEquals("atendida", ana.evaluacion!!.estado)
        assertEquals("Lic. Luis", ana.evaluacion!!.profesional)
        assertEquals(10, ana.tratamiento!!.totalSesiones)
        assertEquals(4, ana.tratamiento!!.sesionesCompletadas)
        assertTrue(ana.pagado)
        assertEquals("09:00", ana.proximaCita!!.hora)

        val beto = f[1]
        assertNull(beto.telefono)
        assertEquals("s1", beto.sedeId)
        assertNull(beto.tratamiento!!.totalSesiones)
        assertEquals(120.5, beto.pago!!.deuda)
        assertFalse(beto.pagado)
        assertNull(beto.proximaCita)

        assertNull(f[2].tratamiento)
        assertNull(f[2].proximaCita!!.hora)
        // Campos que faltan → default; campos nuevos → se ignoran.
        assertNull(f[3].telefono)
        assertNull(f[3].sedeId)
        assertNull(f[3].pago!!.deuda)
    }

    @Test
    fun respuestaVacia() {
        val r = parsearPacientesNuevos("{}")
        assertEquals(0, r.embudo.nuevos)
        assertTrue(r.filas.isEmpty())
    }

    @Test
    fun filtraPorEtapa() {
        val f = parsearPacientesNuevos(respuesta).filas
        assertEquals(4, filtrarPorEtapa(f, null).size)
        assertEquals(listOf("p4"), filtrarPorEtapa(f, EtapaNuevo.SIN_EVALUACION).map { it.pacienteId })
        assertEquals(listOf("p3"), filtrarPorEtapa(f, EtapaNuevo.EVALUADO).map { it.pacienteId })
        assertEquals(listOf("p2"), filtrarPorEtapa(f, EtapaNuevo.EN_TRATAMIENTO).map { it.pacienteId })
        assertEquals(listOf("p1"), filtrarPorEtapa(f, EtapaNuevo.PAGO).map { it.pacienteId })
    }

    @Test
    fun porcentajeEntreEscalones() {
        assertEquals(75, porcentajeEscalon(3, 4))
        assertEquals(67, porcentajeEscalon(2, 3))
        assertNull(porcentajeEscalon(0, 0))
    }

    @Test
    fun mesesDelSelector() {
        val m = mesesRecientes("2026-02-15")
        assertEquals(6, m.size)
        assertEquals("2026-02", m[0].id)
        assertEquals("Este mes", m[0].etiqueta)
        assertEquals("2026-01", m[1].id)
        assertEquals("2025-12", m[2].id)
        assertEquals("Dic 2025", m[2].etiqueta)
        assertEquals("2025-09", m[5].id)
        assertTrue(mesesRecientes("no-es-fecha").isEmpty())
    }

    @Test
    fun mensajesDeError() {
        assertEquals("Tu plan no incluye Pacientes nuevos", mensajeErrorPacientesNuevos(402, """{"error":"Tu plan no incluye Pacientes nuevos"}"""))
        assertEquals("El mes elegido no es válido.", mensajeErrorPacientesNuevos(400, """{"codigo":"PERIODO_INVALIDO"}"""))
        assertEquals("No tienes permiso para ver este reporte.", mensajeErrorPacientesNuevos(403, ""))
        assertEquals("Este reporte todavía no está disponible. Inténtalo más tarde.", mensajeErrorPacientesNuevos(404, "<html>"))
    }
}

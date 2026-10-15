package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 😁 Mi ortodoncia (portal): el JSON del contrato de la web
 * (docs/app-contrato-portal-paciente.md §10) y la tolerancia a un servidor viejo.
 */
class MiOrtodonciaPortalTest {

    private val ejemplo = """
        {"planes":[{"id":"p1","aparatologia":{"id":"brackets_metalicos","nombre":"Brackets metálicos"},"arcadas":"Ambas arcadas",
        "fecha_instalacion":"2026-01-15","retirado":false,"fase":{"id":"cierre_espacios","nombre":"Cierre de espacios"},
        "fases":[{"id":"alineacion","nombre":"Alineación y nivelación","estado":"hecha"},{"id":"cierre_espacios","nombre":"Cierre de espacios","estado":"actual"},
        {"id":"correccion","nombre":"Corrección de clase / mordida","estado":"pendiente"},{"id":"finalizacion","nombre":"Detallado y finalización","estado":"pendiente"},
        {"id":"retencion","nombre":"Retención","estado":"pendiente"}],
        "meses":8,"duracion_estimada_meses":24,"progreso_pct":33,"progreso_texto":"Mes 9 de 24",
        "proximo_control":{"fecha":"2026-10-25","hora":"16:00","origen":"agenda"},"ultimo_control":"2026-09-26",
        "elasticos":["Elásticos de clase II en ambos lados: úsalos todo el día.","Cámbialos 2 o 3 veces al día."],
        "consejos":["Cepíllate después de cada comida."],"retencion":"Fija (lingual)",
        "clinica":{"nombre":"Clínica Dental Sonríe","logo_url":"https://x/logo.png","color_principal":"#0f766e"},"profesional":"Dra. Ortiz"}]}
    """.trimIndent()

    @Test fun elContratoDeLaWeb() {
        val d = parsearMiOrtodoncia(ejemplo)!!
        assertTrue(d.tieneDatos)
        val p = d.planes.single()
        assertEquals("Brackets metálicos", p.aparatologia)
        assertEquals("Mes 9 de 24", p.progresoTexto)
        assertEquals(33, p.progresoPct)
        assertEquals(listOf("hecha", "actual", "pendiente", "pendiente", "pendiente"), p.fases.map { it.estado })
        assertEquals("Cierre de espacios", p.fase?.nombre)
        assertTrue(p.proximoControl!!.agendado)
        assertEquals("16:00", p.proximoControl!!.hora)
        assertEquals(2, p.elasticos.size)
        assertEquals("Clínica Dental Sonríe", p.clinicaNombre)
        assertEquals("#0f766e", p.clinicaColor)
        assertEquals("📅 25/10/2026 · 16:00", textoProximoControlPortal(p) { pe.saniape.app.data.staff.fechaIndicacion(it) })
    }

    @Test fun estimadoSinFechaYRetencion() {
        val base = parsearMiOrtodoncia(ejemplo)!!.planes.single()
        val est = base.copy(proximoControl = ProximoControlPortal("2026-11-02", null, "estimado"))
        assertEquals("≈ 02/11/2026 (aproximado, confírmalo con tu clínica)", textoProximoControlPortal(est) { pe.saniape.app.data.staff.fechaIndicacion(it) })
        assertEquals("Agenda tu próximo control con tu clínica.", textoProximoControlPortal(base.copy(proximoControl = null)) { it })
        assertNull(textoProximoControlPortal(base.copy(retirado = true)) { it })
    }

    @Test fun servidorViejoOVacio() {
        assertFalse(parsearMiOrtodoncia("""{"planes":[]}""")!!.tieneDatos)
        assertNull(parsearMiOrtodoncia("no es json"))
        // Sin `fases`: se arman desde la fase actual; sin id, el plan se salta; color inválido fuera.
        val d = parsearMiOrtodoncia("""{"planes":[{"id":"a","fase":{"id":"correccion","nombre":"Corrección"},"clinica":{"color_principal":"red"}},{"fase":{"id":"x"}}]}""")!!
        assertEquals(1, d.planes.size)
        assertEquals(listOf("hecha", "hecha", "actual", "pendiente", "pendiente"), d.planes[0].fases.map { it.estado })
        assertNull(d.planes[0].clinicaColor)
        assertEquals("Ortodoncia", d.planes[0].aparatologia)
    }
}

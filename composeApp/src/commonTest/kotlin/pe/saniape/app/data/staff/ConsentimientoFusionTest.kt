package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** ✍ Consentimiento y 🔀 Fusionar en el ⋯ de la ficha (paridad web, 11/11/2026). */
class ConsentimientoFusionTest {
    @Test
    fun consentimiento_enlace_eleccion_sin_plantilla_y_error() {
        val ok = ConsentimientoFirmaRepo.interpretar(200, """{"url":"https://x/firmar/abc","token":"abc","vistaPrevia":"…"}""")
        assertEquals(ConsentimientoFirmaRepo.R.Enlace("https://x/firmar/abc", "abc"), ok)

        val elegir = ConsentimientoFirmaRepo.interpretar(200, """{"elegir":true,"documentos":[{"id":"a","nombre":"Estético"},{"id":"b","nombre":"Compromiso","por_defecto":true}]}""")
        assertIs<ConsentimientoFirmaRepo.R.Elegir>(elegir)
        assertEquals(listOf("b", "a"), elegir.documentos.map { it.id })   // el de siempre primero

        assertEquals(ConsentimientoFirmaRepo.R.SinPlantilla, ConsentimientoFirmaRepo.interpretar(404, """{"error":"Sin plantilla","sinPlantilla":true}"""))
        assertIs<ConsentimientoFirmaRepo.R.Error>(ConsentimientoFirmaRepo.interpretar(500, "no-json"))
    }

    @Test
    fun fusion_previa_lo_que_se_mueve_y_los_choques() {
        val r = FusionarFichaRepo.interpretarPrevia(200, """
            {"previa":{"conteos":{"citas":{"conservar":2,"absorber":3},"recetas":{"conservar":1,"absorber":0},"pagos_tratamiento":{"conservar":0,"absorber":1}},
             "choques":[{"codigo":"DNI_DISTINTO","bloquea":false,"detalle":"Los DNI son distintos"},{"codigo":"FUSION_EMBARAZOS_ACTIVOS","bloquea":true,"detalle":"Las dos tienen un embarazo en curso"}]}}
        """.trimIndent())
        assertIs<FusionarFichaRepo.Previa.Ok>(r)
        assertEquals(listOf("citas" to 3, "pagos" to 1), r.previa.seMueve)
        assertTrue(r.previa.bloqueada)

        val solo403 = FusionarFichaRepo.interpretarPrevia(403, """{"error":"Solo un Admin puede fusionar fichas","codigo":"SOLO_ADMIN"}""")
        assertIs<FusionarFichaRepo.Previa.Error>(solo403)
    }

    @Test
    fun clave_de_intento_valida_para_el_servidor() {
        val k = FusionarFichaRepo.nuevaClave()
        assertTrue(k.length in 8..100)
        assertFalse(k == FusionarFichaRepo.nuevaClave())
    }

    @Test
    fun menu_consentimiento_con_permiso_pacientes_y_fusionar_solo_admin() {
        val conTodo = accionesMenuFicha(inactivo = false, puedeInvitar = false, conTelefono = true, puedeEditarPacientes = true, puedeFusionar = true)
        assertTrue(AccionFicha.Consentimiento in conTodo.enMenu)
        assertTrue(AccionFicha.Fusionar in conTodo.enMenu)
        val sinAdmin = accionesMenuFicha(inactivo = false, puedeInvitar = false, conTelefono = true, puedeEditarPacientes = true)
        assertFalse(AccionFicha.Fusionar in sinAdmin.enMenu)
        val deBaja = accionesMenuFicha(inactivo = true, puedeInvitar = false, conTelefono = true, puedeEditarPacientes = true, puedeFusionar = true)
        assertFalse(AccionFicha.Consentimiento in deBaja.enMenu)
        val clinico = accionesMenuFicha(inactivo = false, puedeInvitar = false, conTelefono = true, conDatos = false, puedeEditarPacientes = true, puedeFusionar = true)
        assertFalse(AccionFicha.Consentimiento in clinico.enMenu)
        assertFalse(AccionFicha.Fusionar in clinico.enMenu)
    }
}

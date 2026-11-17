package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fichas SIN documento con el mismo nombre (Dr. Rodriguez Moya, 17/11/2026). */
class FichasSinDocumentoTest {

    @Test
    fun leeLasFichasDelServidor() {
        val cuerpo = """{"resultados":[
            {"id":"a1","nombre":"Gamarra Pita Carlos Andres","fecha_nacimiento":"1988-01-27","edad":null,"citas":7},
            {"id":"b2","nombre":"Gamarra Pita Carlos Andres","fecha_nacimiento":null,"edad":38,"citas":0}
        ]}"""
        val l = interpretarFichasSinDocumento(200, cuerpo)
        assertEquals(2, l.size)
        assertEquals("a1", l[0].id)
        assertEquals(7, l[0].citas)
        assertEquals("nació el 27/01/1988 · 7 atenciones", datosFichaSinDocumento(l[0]))
        assertEquals("38 años · sin atenciones", datosFichaSinDocumento(l[1]))
    }

    @Test
    fun unErrorNoFrenaElAlta() {
        assertTrue(interpretarFichasSinDocumento(503, """{"error":"x"}""").isEmpty())
        assertTrue(interpretarFichasSinDocumento(200, "no es json").isEmpty())
        assertTrue(interpretarFichasSinDocumento(404, "").isEmpty())
    }

    @Test
    fun unaAtencionEnSingular() {
        assertEquals("sin atenciones", datosFichaSinDocumento(FichaSinDocumento("x", "A B", null, null, 0)))
        assertEquals("1 atención", datosFichaSinDocumento(FichaSinDocumento("x", "A B", null, null, 1)))
    }

    @Test
    fun asignarDevuelveLaFicha() {
        assertEquals("a1", interpretarAsignarDocumento(200, """{"ok":true,"id":"a1"}""").id)
    }

    @Test
    fun siOtraFichaYaTieneElDocumentoSeEligeEsa() {
        val r = interpretarAsignarDocumento(409, """{"error":"Ese documento ya es de otra ficha","codigo":"DOCUMENTO_EN_USO","id":"z9"}""")
        assertEquals("z9", r.id)
    }

    @Test
    fun otrosErroresTraenSuMensaje() {
        val r = interpretarAsignarDocumento(409, """{"error":"Esa ficha ya tiene documento","codigo":"YA_TIENE_DOCUMENTO"}""")
        assertNull(r.id)
        assertEquals("Esa ficha ya tiene documento", r.error)
        assertEquals("No se pudo guardar el documento.", interpretarAsignarDocumento(500, "").error)
    }
}

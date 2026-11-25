package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Gemelo de lib/__tests__/agendar-con-telefono.test.ts (web, 24/11/2026). */
class AgendarConTelefonoTest {
    @Test fun soloElTelefono() {
        assertEquals(TextoYTelefono("", "987654321"), separarTelefono("987654321"))
        assertEquals(TextoYTelefono("", "987654321"), separarTelefono(" 987 654 321 "))
        assertEquals(TextoYTelefono("", "987654321"), separarTelefono("+51 987-654-321"))
        assertEquals(TextoYTelefono("", "+59171234567"), separarTelefono("+591 71234567"))
    }

    @Test fun nombreYTelefonoEnCualquierOrden() {
        assertEquals(TextoYTelefono("Ana Quispe", "987654321"), separarTelefono("Ana Quispe 987654321"))
        assertEquals(TextoYTelefono("ana quispe", "987654321"), separarTelefono("987 654 321 ana quispe"))
    }

    @Test fun unDniUnRutOUnNumeroCualquieraNoSonTelefono() {
        for (t in listOf("44291234", "9.876.543-2", "12.345.678-9", "98765432-1", "4424", "Ana Quispe", "9876543210", "887654321", "AB1234567")) {
            assertEquals(TextoYTelefono(t, null), separarTelefono(t), t)
        }
    }

    @Test fun coincideConSeparadoresYCodigoDePais() {
        assertTrue(coincidePorTelefono("987 654 321", "987654321"))
        assertTrue(coincidePorTelefono("+51 987-654-321", "987654321"))
        assertFalse(coincidePorTelefono("987654322", "987654321"))
        assertFalse(coincidePorTelefono(null, "987654321"))
    }

    @Test fun nombreProvisional() {
        assertEquals("Sin nombre · 987 654 321", nombreProvisional("987654321"))
        assertEquals("+59171234567", telefonoLegible("+59171234567"))
    }
}

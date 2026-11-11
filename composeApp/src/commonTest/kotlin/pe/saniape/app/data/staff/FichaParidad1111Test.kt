package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Paridad del ⋯ de la ficha con la web (11/11/2026): «📋 Filiación ⚠ N» con la
 * misma regla que `faltantesFiliacion` de la web, y «📱 QR datos».
 */
class FichaParidad1111Test {

    private val completa = DatosFiliacion(
        dni = "40125874", fechaNacimiento = "1985-03-10", sexoPaciente = "F", direccion = "Jr. Bolívar 638",
        telefono = "987654321", ocupacion = "Docente",
        hc = mapOf("estado_civil" to "Casada", "grado_instruccion" to "Superior", "lugar_nacimiento" to "Tumbes"),
    )

    @Test
    fun hojaCompletaNoFaltaNada() {
        assertEquals(emptyList(), faltantesFiliacion(completa, "2026-11-11"))
        assertEquals("📋 Filiación (historia clínica)", etiquetaFiliacion(0))
    }

    @Test
    fun mismosTextosYOrdenQueLaWeb() {
        val f = faltantesFiliacion(DatosFiliacion(), "2026-11-11")
        assertEquals(
            listOf("Documento de identidad", "Fecha de nacimiento", "Sexo", "Domicilio", "Teléfono",
                "Estado civil", "Grado de instrucción", "Ocupación", "Lugar de nacimiento"),
            f,
        )
        assertEquals("📋 Filiación ⚠ 9", etiquetaFiliacion(f.size))
    }

    @Test
    fun elSexoDeLaFichaTambienCuenta() {
        val sinSexoEnHc = completa.copy(hc = completa.hc - "sexo")
        assertTrue("Sexo" !in faltantesFiliacion(sinSexoEnHc, "2026-11-11"))
        assertTrue("Sexo" in faltantesFiliacion(sinSexoEnHc.copy(sexoPaciente = null), "2026-11-11"))
    }

    @Test
    fun menorSinResponsableConDni() {
        val menor = completa.copy(fechaNacimiento = "2015-06-01")
        assertTrue("Padre, madre o tutor (con DNI)" in faltantesFiliacion(menor, "2026-11-11"))
        val conTutor = menor.copy(hc = menor.hc + ("responsable_nombre" to "Ana Pérez") + ("responsable_dni" to "40000000"))
        assertTrue("Padre, madre o tutor (con DNI)" !in faltantesFiliacion(conTutor, "2026-11-11"))
    }

    @Test
    fun qrDatosSoloConPermisoDePacientesYFichaActiva() {
        val con = accionesMenuFicha(inactivo = false, puedeInvitar = false, conTelefono = false, puedeEditarPacientes = true)
        assertTrue(AccionFicha.QrDatos in con.enMenu)
        assertTrue(AccionFicha.QrDatos !in accionesMenuFicha(inactivo = false, puedeInvitar = false, conTelefono = false).enMenu)
        assertTrue(AccionFicha.QrDatos !in accionesMenuFicha(inactivo = true, puedeInvitar = false, conTelefono = false, puedeEditarPacientes = true).enMenu)
        assertTrue(AccionFicha.QrDatos !in accionesMenuFicha(inactivo = false, puedeInvitar = false, conTelefono = false, conDatos = false, puedeEditarPacientes = true).enMenu)
    }

    @Test
    fun respuestaDelQr() {
        assertEquals(QrDatosRepo.R.Ok("https://www.saniape.com/datos/abc"),
            QrDatosRepo.interpretar(200, """{"token":"abc","url":"https://www.saniape.com/datos/abc","expira":"2026-11-12T15:00:00Z"}"""))
        assertEquals(QrDatosRepo.R.Error("No tienes permiso para editar pacientes"),
            QrDatosRepo.interpretar(403, """{"error":"No tienes permiso para editar pacientes"}"""))
        assertTrue(QrDatosRepo.interpretar(500, "") is QrDatosRepo.R.Error)
        assertEquals("Hola Rosa, actualiza tus datos de contacto aquí (vale por 24 horas): https://x/datos/1",
            QrDatosRepo.mensajeWhatsApp("Rosa Quispe", "https://x/datos/1"))
    }
}

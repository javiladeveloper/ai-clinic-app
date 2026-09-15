package pe.saniape.app.ui.clinica.pacientes

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Buscar un paciente por nombre.
 *
 * El caso que lo motivó (reportado en la web, 2026-09-15): recepción escribe
 * "jorge oli" y no aparece nadie. El paciente se llama "JORGE YOCELYN OLIVERA
 * GAMERO" y la búsqueda era `nombre.contains(busqueda)` —la frase literal—,
 * así que entre "jorge" y "oli" estorbaba el segundo nombre.
 *
 * No es un caso raro: en producción 399 de 873 pacientes (46%) tienen cuatro o
 * más palabras en el nombre, y 137 llevan alguna tilde. La app tenía el mismo
 * defecto que la web, en su propio código.
 */
class BuscarPacienteTest {

    private val JORGE = "JORGE YOCELYN OLIVERA GAMERO"
    private val MARIA = "MARÍA FERNÁNDEZ SOTO"

    private fun coincide(nombre: String, q: String, dni: String? = null, diag: String? = null) =
        coincideBusqueda(nombre, dni, diag, q)

    @Test
    fun encuentra_por_nombre_y_apellido_salteando_los_del_medio() {
        // EL caso reportado. Antes daba false.
        assertTrue(coincide(JORGE, "jorge oli"))
    }

    @Test
    fun encuentra_con_las_palabras_en_cualquier_orden() {
        // Nadie teclea en el orden exacto del registro.
        assertTrue(coincide(JORGE, "olivera jorge"))
        assertTrue(coincide(JORGE, "gamero"))
    }

    @Test
    fun encuentra_sin_tildes_lo_escrito_con_tildes() {
        // 137 pacientes llevan alguna y nadie las teclea al buscar.
        assertTrue(coincide(MARIA, "maria fernandez"))
        assertTrue(coincide(MARIA, "MARIA"))
    }

    @Test
    fun la_ene_tambien_se_normaliza() {
        assertTrue(coincide("PEDRO NÚÑEZ SOTO", "nunez"))
    }

    @Test
    fun no_confunde_a_dos_pacientes_distintos() {
        // Todas las palabras tienen que estar: "jorge soto" no es ninguno,
        // aunque "jorge" sea de uno y "soto" del otro.
        assertFalse(coincide(JORGE, "jorge soto"))
        assertFalse(coincide(MARIA, "jorge soto"))
    }

    @Test
    fun sigue_encontrando_por_documento() {
        assertTrue(coincide(JORGE, "40123456", dni = "40123456"))
        assertTrue(coincide(JORGE, "4012", dni = "40123456"))
        // Pegado con un espacio de más: el documento se busca entero.
        assertTrue(coincide(JORGE, "401 23456", dni = "40123456"))
    }

    @Test
    fun el_documento_no_se_ve_si_no_hay_permiso() {
        // La pantalla pasa dni = null cuando el rol no ve datos de contacto.
        assertFalse(coincide(JORGE, "40123456", dni = null))
    }

    @Test
    fun encuentra_por_diagnostico() {
        assertTrue(coincide(JORGE, "lumbalgia", diag = "Lumbalgia mecánica L4-L5"))
        // Y sin tildes también.
        assertTrue(coincide(JORGE, "mecanica", diag = "Lumbalgia mecánica L4-L5"))
    }

    @Test
    fun sin_texto_no_filtra_a_nadie() {
        assertTrue(coincide(JORGE, ""))
        assertTrue(coincide(JORGE, "   "))
    }

    @Test
    fun espacios_de_mas_no_rompen_la_busqueda() {
        assertTrue(coincide(JORGE, "  jorge   olivera  "))
    }
}

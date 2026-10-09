package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tipo de documento del paciente (pacientes.tipo_documento) — gemelo de
 * regional-paciente.ts (web) — y país/zona de la clínica en Ajustes.
 */
class TiposDocumentoTest {

    @Test
    fun valores_iguales_a_la_web_y_a_la_migracion() {
        assertEquals(listOf("DNI", "Carné de extranjería", "Pasaporte", "CI", "RUT", "Otro"), TIPOS_DOCUMENTO_PACIENTE)
    }

    @Test
    fun normaliza_alias_y_rechaza_lo_desconocido() {
        assertEquals("Carné de extranjería", normalizarTipoDocumento("CE"))
        assertEquals("Carné de extranjería", normalizarTipoDocumento("carné de extranjería"))
        assertEquals("Pasaporte", normalizarTipoDocumento("pasaporte"))
        assertEquals("CI", normalizarTipoDocumento(" ci "))
        assertNull(normalizarTipoDocumento(""))
        assertNull(normalizarTipoDocumento(null))
        assertNull(normalizarTipoDocumento("Licencia"))
    }

    @Test
    fun opciones_por_pais_peru_como_siempre() {
        assertEquals(listOf("DNI", "RUT", "Pasaporte"), opcionesDocumento("PE").map { it.first })
        assertEquals(listOf("Carné de extranjería", "CI", "Otro"), opcionesDocumentoExtra("PE").map { it.first })
        assertEquals(listOf("CI", "Pasaporte"), opcionesDocumento("BO").map { it.first })
        assertEquals(listOf("Carné de extranjería", "DNI", "RUT", "Otro"), opcionesDocumentoExtra("BO").map { it.first })
        // País sin documento propio: "" (se guarda NULL = el del país).
        assertEquals("", opcionesDocumento("CO").first().first)
        val todos = todasOpcionesDocumento("PE").map { it.first }
        assertEquals(todos.size, todos.toSet().size)
        TIPOS_DOCUMENTO_PACIENTE.forEach { assertTrue(it in todos) }
    }

    @Test
    fun deduce_el_tipo_y_el_guardado_manda() {
        assertEquals("DNI", deducirTipoDocumento("40125874", "PE"))
        assertEquals("RUT", deducirTipoDocumento("12.345.678-9", "PE"))
        assertEquals("DNI", deducirTipoDocumento(null, "PE"))
        assertEquals("CI", deducirTipoDocumento("1234567", "BO"))
        assertEquals("Carné de extranjería", deducirTipoDocumento("001234567", "PE", "Carné de extranjería"))
        assertEquals("DNI", deducirTipoDocumento("40125874", "BO", "DNI"))
        assertEquals("DNI", deducirTipoDocumento("40125874", "PE", "XX"))
    }

    @Test
    fun que_se_guarda() {
        assertEquals("DNI", tipoDocumentoAGuardar("DNI", "40125874"))
        assertNull(tipoDocumentoAGuardar("Pasaporte", " "))
        assertNull(tipoDocumentoAGuardar("", "AB12345"))
        assertEquals("CI", tipoDocumentoAGuardar("CI", "1234567"))
    }

    @Test
    fun reniec_solo_dni_en_peru() {
        assertTrue(buscaPadronDoc("PE", "DNI"))
        assertTrue(buscaPadronDoc(null, "DNI"))
        assertFalse(buscaPadronDoc("PE", "RUT"))
        assertFalse(buscaPadronDoc("BO", "DNI"))
    }

    @Test
    fun etiqueta_con_el_tipo_del_paciente() {
        assertEquals("DNI", etiquetaDocumentoPaciente(null, "PE"))          // DALU
        assertEquals("DNI", etiquetaDocumentoPaciente("DNI", "BO"))         // peruano en Bolivia
        assertEquals("CI", etiquetaDocumentoPaciente(null, "BO"))
        assertEquals("Pasaporte", etiquetaDocumentoPaciente("Pasaporte", "PE"))
        assertEquals("Carné de extranjería", etiquetaDocumentoPaciente("Carné de extranjería", "PE"))
        assertEquals("Documento", etiquetaDocumentoPaciente("Otro", "PE"))
    }

    @Test
    fun pais_de_la_clinica_propone_moneda_y_zona() {
        assertEquals(mapOf("pais" to "BO", "moneda" to "BOB", "zona" to "America/La_Paz"),
            cambiosPaisClinica("PE", "PEN", "America/Lima", "BO"))
        // Ecuador usa dólares: si la clínica ya estaba en USD, la moneda no se manda.
        assertEquals(mapOf("pais" to "EC", "zona" to "America/Guayaquil"),
            cambiosPaisClinica("US", "USD", "America/New_York", "EC"))
        assertTrue(cambiosPaisClinica("PE", "PEN", "America/Lima", "ZZ").isEmpty())
        assertEquals(listOf("America/Lima"), zonasDePais("PE"))
        assertEquals(listOf("America/Bogota", "America/Lima"), zonasDePais("PE", "America/Bogota"))
    }
}

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
    fun solo_se_guarda_lo_elegido_a_mano() {
        assertEquals("DNI", tipoDocumentoAGuardar("DNI", "40125874", tocado = true))
        assertEquals("CI", tipoDocumentoAGuardar("CI", "1234567", tocado = true))
        // Adivinado (DNI por defecto, RUT deducido): no se guarda ni se manda.
        assertNull(tipoDocumentoAGuardar("DNI", "40125874", tocado = false))
        assertNull(tipoDocumentoAGuardar("RUT", "AB123456", tocado = false))
        assertNull(tipoDocumentoAGuardar("Pasaporte", " ", tocado = true))
        assertNull(tipoDocumentoAGuardar("", "AB12345", tocado = true))
    }

    @Test
    fun para_mostrar_el_de_la_hc_antes_que_el_deducido() {
        assertEquals("Pasaporte", deducirTipoDocumento("QX99880022", "PE", null, "Pasaporte"))
        assertEquals("RUT", deducirTipoDocumento("QX99880022", "PE", "RUT", "Pasaporte"))
        // El 'DNI' automático de la HC no manda (puede ser un CI de 8 dígitos).
        assertEquals("CI", deducirTipoDocumento("12345678", "BO", null, "DNI"))
        assertNull(tipoDocumentoDeHc("Sin documento"))
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
        // Como la web: sin tipo, un documento que no es DNI en Perú → "Documento"; la HC ayuda.
        assertEquals("Documento", etiquetaDocumentoPaciente(null, "PE", "ZQ9988001"))
        assertEquals("DNI", etiquetaDocumentoPaciente(null, "PE", "40125874"))
        assertEquals("Pasaporte", etiquetaDocumentoPaciente(null, "PE", "ZQ9988001", "Pasaporte"))
    }

    @Test
    fun dalu_no_lee_el_tipo() {
        assertFalse(hayQueLeerTipoDocumento("40125874", multiSede = false))
        assertTrue(hayQueLeerTipoDocumento("40125874", multiSede = true))
        assertTrue(hayQueLeerTipoDocumento("ZQ9988001", multiSede = false))
        assertFalse(hayQueLeerTipoDocumento(null, multiSede = true))
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
        // Confirmación: consecuencias del cambio; sin cambios, nada.
        val c = consecuenciasCambioRegional("PE", "PEN", "America/Lima", "BO", "BOB", "America/La_Paz").joinToString(" ")
        assertTrue("\"CI\" (hoy \"DNI\")" in c)
        assertTrue("+591 (hoy +51)" in c)
        assertTrue("America/La_Paz" in c)
        assertTrue("NO se convierten" in c)
        assertTrue(consecuenciasCambioRegional("PE", "PEN", "America/Lima", "PE", "PEN", "America/Lima").isEmpty())
        // Aviso persistente país ≠ moneda.
        assertNull(avisoPaisMoneda("PE", "PEN"))
        assertTrue(avisoPaisMoneda("BO", "PEN")!!.startsWith("País Bolivia · moneda PEN"))
        assertEquals(listOf("America/Bogota", "America/Lima"), zonasDePais("PE", "America/Bogota"))
    }
}

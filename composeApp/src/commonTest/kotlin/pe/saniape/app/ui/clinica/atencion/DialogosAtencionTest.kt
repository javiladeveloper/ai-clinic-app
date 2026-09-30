package pe.saniape.app.ui.clinica.atencion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import pe.saniape.app.data.staff.DiagnosticoCie
import pe.saniape.app.data.staff.EspecialidadProfesionalApp
import pe.saniape.app.data.staff.ProfesionalPlan
import pe.saniape.app.data.staff.SugerenciaMedicamento
import pe.saniape.app.data.staff.puedePrescribirAprox

/** Piezas puras de DialogoReceta y DialogoFiliacion (gemelas de lib/recetas.ts y lib/historia-clinica.ts). */
class DialogosAtencionTest {

    // ── Receta ──

    @Test
    fun unidadYViaSugeridasComoLaWeb() {
        assertEquals("tabletas", unidadSugerida("Tableta recubierta"))
        assertEquals("cápsulas", unidadSugerida("Cápsula"))
        assertEquals("ampollas", unidadSugerida("Inyectable"))
        assertEquals("tubos", unidadSugerida("Ungüento"))
        assertEquals("óvulos", unidadSugerida("Óvulo"))
        assertEquals("sobres", unidadSugerida("Polvo"))
        assertEquals("frascos", unidadSugerida("Jarabe"))
        assertEquals("", unidadSugerida(null))
        assertEquals("Oral", viaSugerida("Suspensión"))
        assertEquals("Sublingual", viaSugerida("Tableta sublingual"))
        assertEquals("Tópica", viaSugerida("Crema"))
        assertEquals("Ótica", viaSugerida("Solución ótica"))
        assertEquals("", viaSugerida("Inyectable"))
    }

    @Test
    fun filtraSugerenciasPorInicioDeDciOPalabra() {
        val l = listOf(
            SugerenciaMedicamento("Amoxicilina"),
            SugerenciaMedicamento("Amoxicilina + ácido clavulánico"),
            SugerenciaMedicamento("Paracetamol"),
        )
        assertEquals(2, filtrarSugerencias(l, "amox").size)
        assertEquals(listOf("Amoxicilina + ácido clavulánico"), filtrarSugerencias(l, "acido").map { it.dci })
        assertTrue(filtrarSugerencias(l, "a").isEmpty())
    }

    @Test
    fun cuerpoEmitirTraeLasClavesDelContrato() {
        val cuerpo = cuerpoEmitirReceta(
            pacienteId = "p1", terapeutaId = "t1", fecha = "2026-09-30", vigenciaDias = 30,
            diagnostico = " Faringitis aguda ", cie10 = "j02.9", indicacionesGenerales = "Abundantes líquidos",
            infoFarmaceutico = "  ",
            items = listOf(
                ItemRecetaForm(
                    dci = "Paracetamol", concentracion = "500 mg", forma = "Tableta", via = "Oral",
                    dosis = "1 tableta", frecuencia = "cada 8 horas", duracion = "5 días", cantidad = "15",
                    indicaciones = "después de las comidas",
                ),
                ItemRecetaForm(forma = "Tableta", via = "Oral"), // fila en blanco: no viaja
            ),
            citaId = "c1", tratamientoId = null, claveCliente = "k1",
        )
        assertEquals(
            setOf(
                "pacienteId", "terapeutaId", "fecha", "vigenciaDias", "diagnostico", "cie10",
                "indicacionesGenerales", "infoFarmaceutico", "items", "citaId", "tratamientoId", "sesionId", "claveCliente",
            ),
            cuerpo.keys,
        )
        assertEquals(JsonNull, cuerpo["sesionId"])
        assertEquals("Faringitis aguda", cuerpo["diagnostico"]!!.jsonPrimitive.content)
        assertEquals("J02.9", cuerpo["cie10"]!!.jsonPrimitive.content)
        assertEquals(30, cuerpo["vigenciaDias"]!!.jsonPrimitive.int)
        assertEquals(JsonNull, cuerpo["infoFarmaceutico"])
        assertEquals(JsonNull, cuerpo["tratamientoId"])
        val items = cuerpo["items"] as JsonArray
        assertEquals(1, items.size)
        val it0 = items[0] as JsonObject
        assertEquals(
            setOf("dci", "marca", "concentracion", "forma", "via", "dosis", "frecuencia", "duracion", "cantidad", "unidad", "indicaciones"),
            it0.keys,
        )
        assertEquals(15.0, it0["cantidad"]!!.jsonPrimitive.double)
        assertTrue((it0["cantidad"] as JsonPrimitive).isString.not())
        assertEquals("tabletas", it0["unidad"]!!.jsonPrimitive.content) // unidad sugerida si quedó vacía
        assertEquals(JsonNull, it0["marca"])
    }

    @Test
    fun claveClienteEsUuidV4() {
        val k = nuevaClaveCliente()
        assertTrue(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(k), k)
        assertTrue(k != nuevaClaveCliente())
    }

    @Test
    fun prescriptoresYModoIndicaciones() {
        val medico = ProfesionalPlan("m", "Dra. A", cmp = "45678", estado = "Activo",
            especialidades = listOf(EspecialidadProfesionalApp("e1", "medicina_general")))
        val fisio = ProfesionalPlan("f", "Lic. B", cmp = "1234", estado = "Activo",
            especialidades = listOf(EspecialidadProfesionalApp("e2", "fisioterapia")))
        val sinCmp = ProfesionalPlan("x", "Dr. C", cmp = null, estado = "Activo")
        val inactivo = medico.copy(id = "i", estado = "Inactivo")
        // La aproximación de la ficha (el servidor no lo manda ahí todavía).
        assertEquals(listOf("m"), listOf(medico, fisio, sinCmp, inactivo).filter { puedePrescribirAprox(it, recetasOptIn = false) }.map { it.id })
        assertEquals(listOf("m", "f"), listOf(medico, fisio, sinCmp).filter { puedePrescribirAprox(it, recetasOptIn = true) }.map { it.id })
        assertFalse(prescriptorNoMedico(medico))
        assertTrue(prescriptorNoMedico(fisio))
        assertEquals("CMP 45678", colegiaturaImpresa(medico))
        assertEquals("CTMP 1234", colegiaturaImpresa(fisio))
    }

    @Test
    fun elDialogoUsaPuedePrescribirDelServidor() {
        // El servidor manda `puedePrescribir`: el diálogo no recalcula (aunque no tenga CMP o rubro).
        val a = ProfesionalPlan("a", "Dr. A", estado = "Activo", puedePrescribir = true)
        val b = ProfesionalPlan("b", "Dr. B", cmp = "45678", estado = "Activo", puedePrescribir = false,
            especialidades = listOf(EspecialidadProfesionalApp("e1", "medicina_general")))
        val c = ProfesionalPlan("c", "Dra. C", cmp = "11111", estado = "Activo", puedePrescribir = true)
        assertEquals(listOf("a", "c"), prescriptoresReceta(listOf(a, b, c)).map { it.id })
        // El ya elegido se ofrece aunque no pueda (p. ej. el propio profesional bloqueado).
        assertEquals(listOf("a", "b", "c"), prescriptoresReceta(listOf(a, b, c), elegidoId = "b").map { it.id })
    }

    @Test
    fun prescriptorInicialEnOrden() {
        val a = ProfesionalPlan("a", puedePrescribir = true)
        val c = ProfesionalPlan("c", puedePrescribir = true)
        val dos = listOf(a, c)
        // 1) propio bloqueado, aunque no sea elegible
        assertEquals("z", prescriptorInicial(dos, propioBloqueado = "z", sugerido = "a", miTerapeutaId = "c"))
        // 2) el sugerido (el que atiende) si es elegible
        assertEquals("a", prescriptorInicial(dos, propioBloqueado = null, sugerido = "a", miTerapeutaId = "c"))
        // 3) el sugerido no es elegible → mi terapeuta si lo es
        assertEquals("c", prescriptorInicial(dos, propioBloqueado = null, sugerido = "x", miTerapeutaId = "c"))
        // 4) el único elegible
        assertEquals("a", prescriptorInicial(listOf(a), propioBloqueado = null, sugerido = "x", miTerapeutaId = "y"))
        // 5) ninguno
        assertEquals("", prescriptorInicial(dos, propioBloqueado = null, sugerido = null, miTerapeutaId = null))
    }

    @Test
    fun diagnosticoDeLaRecetaComoLaWeb() {
        val (texto, codigo) = diagnosticoParaReceta(
            listOf(
                DiagnosticoCie(null, "Cefalea tensional", "P"),
                DiagnosticoCie("j02.9", "Faringitis  aguda", "P"),
                DiagnosticoCie("M54.5", "Lumbalgia", "D"),
                DiagnosticoCie(null, "  ", "P"), // vacío: no cuenta
                DiagnosticoCie("J02.9", "Faringitis aguda", "P"), // repetido: no cuenta
            ),
        )
        assertEquals("Cefalea tensional; Faringitis aguda; Lumbalgia", texto)
        assertEquals("J02.9", codigo) // el PRIMERO con código
        assertEquals(null to null, diagnosticoParaReceta(emptyList()))
    }

    @Test
    fun filasDeLaRecetaTienenIdentidadPropia() {
        val a = ItemRecetaForm()
        val b = ItemRecetaForm()
        assertTrue(a.uid != b.uid)
        assertEquals(a.uid, a.copy(dci = "Paracetamol").uid)
    }

    // ── Filiación ──

    @Test
    fun filiacionSoloMandaLoTocadoOConValor() {
        val valores = mapOf(
            "sexo" to "F", // con valor, sin tocar (vino de la HC): viaja igual
            "estado_civil" to "", // tocado y vaciado → null
            "religion" to "  ", // sin tocar y vacío → no viaja
            "lugar_nacimiento" to " Tacna ",
        )
        val cuerpo = cuerpoFiliacion(valores, tocados = setOf("estado_civil", "lugar_nacimiento", "procedencia"))
        assertEquals(
            mapOf("sexo" to "F", "estado_civil" to null, "lugar_nacimiento" to "Tacna", "procedencia" to null),
            cuerpo,
        )
        assertTrue(cuerpo.keys.all { it in CAMPOS_FILIACION })
    }

    @Test
    fun faltantesSeVanAlCompletarLaHoja() {
        val faltan = listOf("Documento de identidad", "Sexo", "Estado civil", FALTA_RESPONSABLE)
        val v = mapOf("sexo" to "M", "responsable_nombre" to "Ana", "responsable_dni" to "")
        assertEquals(listOf("Documento de identidad", "Estado civil", FALTA_RESPONSABLE), faltantesVigentes(faltan, v))
        assertEquals(listOf("Documento de identidad", "Estado civil"),
            faltantesVigentes(faltan, v + ("responsable_dni" to "40125874")))
    }
}

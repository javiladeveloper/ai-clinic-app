package pe.saniape.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pe.saniape.app.data.staff.AperturaIndicaciones
import pe.saniape.app.data.staff.BorradorIndicacion
import pe.saniape.app.data.staff.ContenidoEditorIndicaciones
import pe.saniape.app.data.staff.FlujoIndicacionesFicha
import pe.saniape.app.data.staff.IndicacionPaciente
import pe.saniape.app.data.staff.LimitesIndicaciones
import pe.saniape.app.data.staff.PlantillaBaseIndicaciones
import pe.saniape.app.data.staff.borradorIndicacionDesdeEditor
import pe.saniape.app.data.staff.candidataIndicacionesTrasCompletar
import pe.saniape.app.data.staff.catalogoIndicacionesDeJson
import pe.saniape.app.data.staff.coincideBusquedaIndicacion
import pe.saniape.app.data.staff.colorHojaIndicaciones
import pe.saniape.app.data.staff.contenidoEditorDesde
import pe.saniape.app.data.staff.cuerpoIndicacion
import pe.saniape.app.data.staff.cuerpoPlantillaIndicaciones
import pe.saniape.app.data.staff.enlaceWhatsApp
import pe.saniape.app.data.staff.fechaIndicacion
import pe.saniape.app.data.staff.frasesCampoIndicacion
import pe.saniape.app.data.staff.htmlIndicaciones
import pe.saniape.app.data.staff.indicacionDeJson
import pe.saniape.app.data.staff.limpiarLineasIndicacion
import pe.saniape.app.data.staff.lineasIndicacion
import pe.saniape.app.data.staff.ofrecerIndicacionesTrasCompletar
import pe.saniape.app.data.staff.ordenarIndicaciones
import pe.saniape.app.data.staff.plantillaIndicacionesDeJson
import pe.saniape.app.data.staff.seccionesIndicacion
import pe.saniape.app.data.staff.sugerenciasIndicacionesDeJson
import pe.saniape.app.data.staff.textoIndicacionesWhatsApp
import pe.saniape.app.data.staff.validarIndicacion
import pe.saniape.app.data.staff.validarPlantillaIndicaciones

/** Gemelo de lib/__tests__/indicaciones.test.ts (+ catálogo y regla de la agenda). */
class IndicacionesTest {

    private val base = BorradorIndicacion(pacienteId = "p1", titulo = "Cuidados después del peeling", indicaciones = "Hidratar")

    private fun ind(
        fecha: String = "2026-10-15", titulo: String = "Cuidados después de una extracción dental",
        indicaciones: String? = "Muerda la gasa 30 minutos\nComa blando y frío", evitar: String? = "Usar sorbete",
        signos: String? = "Sangrado que no para", control: String? = "Control en 7 días",
    ) = IndicacionPaciente(
        id = "i1", pacienteId = "p1", fecha = fecha, titulo = titulo,
        indicaciones = indicaciones, evitar = evitar, signosAlarma = signos, control = control,
    )

    // ─── validarIndicacion ───

    @Test fun pideTituloYAlMenosUnContenido() {
        assertTrue(validarIndicacion(BorradorIndicacion("p1"))[0].contains("título"))
        assertEquals(
            listOf("Escribe al menos una indicación (o qué evitar, los signos de alarma o el control)."),
            validarIndicacion(BorradorIndicacion("p1", titulo = "X")),
        )
        assertEquals(emptyList(), validarIndicacion(BorradorIndicacion("p1", titulo = "X", control = "Control en 7 días")))
        assertEquals(emptyList(), validarIndicacion(base))
    }

    @Test fun pacienteYFecha() {
        assertTrue("Falta el paciente." in validarIndicacion(base.copy(pacienteId = "")))
        assertTrue("Fecha inválida." in validarIndicacion(base.copy(fecha = "14/10/2026")))
    }

    @Test fun limitesDeTexto() {
        assertTrue(validarIndicacion(base.copy(titulo = "x".repeat(LimitesIndicaciones.TITULO + 1)))[0].contains("título es muy largo"))
        assertTrue(validarIndicacion(base.copy(evitar = "x".repeat(4001)))[0].contains("evitar"))
        assertTrue(validarIndicacion(base.copy(signosAlarma = "x".repeat(4001)))[0].contains("alarma"))
        assertTrue(validarIndicacion(base.copy(control = "x".repeat(501)))[0].contains("control"))
    }

    @Test fun plantillaNombreObligatorioYContenido() {
        val i = ind()
        assertTrue("Ponle un nombre a la plantilla." in validarPlantillaIndicaciones("", i))
        assertTrue(validarPlantillaIndicaciones("x".repeat(81), i)[0].contains("nombre es muy largo"))
        assertEquals(emptyList(), validarPlantillaIndicaciones("Post peeling", i))
    }

    // ─── limpieza y presentación ───

    @Test fun lineasYSecciones() {
        assertNull(limpiarLineasIndicacion("\n  \n"))
        assertEquals("Hidratar\nProtector solar FPS 50", limpiarLineasIndicacion("- Hidratar\n\n• Protector solar  FPS 50 \n"))
        assertEquals(listOf("a", "b"), lineasIndicacion("a\n- b\n\n"))
        assertEquals(
            listOf("indicaciones" to 1, "signos_alarma" to 2),
            seccionesIndicacion(ind(indicaciones = "a", evitar = null, signos = "Fiebre\nPus")).map { it.clave to it.items.size },
        )
    }

    @Test fun fechaDeterministaYOrden() {
        assertEquals("05/10/2026", fechaIndicacion("2026-10-05"))
        val l = ordenarIndicaciones(listOf(
            ind(fecha = "2026-10-01").copy(id = "a", createdAt = "2026-10-01T10:00:00Z"),
            ind(fecha = "2026-10-05").copy(id = "b", createdAt = "2026-10-05T09:00:00Z"),
            ind(fecha = "2026-10-05").copy(id = "c", createdAt = "2026-10-05T11:00:00Z"),
        ))
        assertEquals(listOf("c", "b", "a"), l.map { it.id })
    }

    // ─── WhatsApp: salida IDÉNTICA a la web ───

    @Test fun whatsappConSeccionesVinetasControlYFirma() {
        val esperado = listOf(
            "📝 *Cuidados después de una extracción dental* — Sonrisas",
            "Hola Ana, estas son sus indicaciones del 15/10/2026.",
            "", "✅ *Qué hacer en casa*", "• Muerda la gasa 30 minutos", "• Coma blando y frío",
            "", "🚫 *Evite*", "• Usar sorbete",
            "", "🚨 *Acuda de inmediato si…*", "• Sangrado que no para",
            "", "📅 *Control:* Control en 7 días",
            "", "— Dr. Ruiz",
        ).joinToString("\n")
        val i = ind()
        assertEquals(esperado, textoIndicacionesWhatsApp(i.fecha, i, clinica = "Sonrisas", paciente = "Ana Torres", profesional = "Dr. Ruiz"))
    }

    @Test fun whatsappSinDatosOpcionalesNoDejaSeccionesVacias() {
        val i = ind(evitar = null, signos = " ", control = null)
        assertEquals(
            listOf(
                "📝 *Cuidados después de una extracción dental*", "Estas son sus indicaciones del 15/10/2026.",
                "", "✅ *Qué hacer en casa*", "• Muerda la gasa 30 minutos", "• Coma blando y frío",
            ).joinToString("\n"),
            textoIndicacionesWhatsApp(i.fecha, i),
        )
    }

    @Test fun whatsappPorApiWhatsappNuncaWaMe() {
        val i = ind()
        val url = enlaceWhatsApp("987654321", textoIndicacionesWhatsApp(i.fecha, i))
        assertNotNull(url)
        assertTrue(url.startsWith("https://api.whatsapp.com/send?phone=51987654321&text="), url)
        assertFalse(url.contains("wa.me"))
    }

    // ─── Hoja imprimible ───

    @Test fun hojaEscapaTodoYSoloLogoHttpsYColorHex() {
        val i = ind(titulo = "<script>x</script>", signos = "Fiebre & <b>pus</b>")
        val h = htmlIndicaciones(i.fecha, i, "Clínica \"A\"", null, "http://x/logo.png", "red;}body{", "Ana <Torres>", "123", "Dr. <Ruiz>")
        assertFalse(h.contains("<script>x"))
        assertTrue(h.contains("&lt;script&gt;x&lt;/script&gt;"))
        assertTrue(h.contains("Fiebre &amp; &lt;b&gt;pus&lt;/b&gt;"))
        assertFalse(h.contains("http://x/logo.png"))
        assertFalse(h.contains("red;}"))
        assertTrue(h.contains("#2c3e7a"))
        assertTrue(h.contains("INDICACIONES PARA CASA"))
        assertTrue(h.contains("Acuda de inmediato o llámenos si"))
        val conLogo = htmlIndicaciones(i.fecha, i, "A", null, "https://x/logo.png", "#0f766e", null, null, null)
        assertTrue(conLogo.contains("https://x/logo.png"))
        assertTrue(conLogo.contains("#0f766e"))
        assertEquals("#2c3e7a", colorHojaIndicaciones("#0f76"))
    }

    // ─── Cuerpos de los pedidos ───

    @Test fun cuerpoPostConCitaYEspecialidadPatchSinEllas() {
        val b = borradorIndicacionDesdeEditor(
            "p1", "2026-10-15",
            ContenidoEditorIndicaciones(titulo = " Post ", indicaciones = "Hidratar", evitar = "  "),
            visiblePaciente = false, citaId = "c1", especialidadId = "e1",
        )
        val post = cuerpoIndicacion(b)
        assertEquals("p1", post["pacienteId"]!!.jsonPrimitive.content)
        assertEquals("c1", post["citaId"]!!.jsonPrimitive.content)
        assertEquals("e1", post["especialidadId"]!!.jsonPrimitive.content)
        assertEquals("Post", post["titulo"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, post["evitar"])
        assertEquals("false", post["visiblePaciente"]!!.jsonPrimitive.content)
        val patch = cuerpoIndicacion(b, id = "i1")
        assertEquals("i1", patch["id"]!!.jsonPrimitive.content)
        assertNull(patch["pacienteId"]); assertNull(patch["citaId"]); assertNull(patch["especialidadId"])
        val pl = cuerpoPlantillaIndicaciones(" Post peeling ", ind(), null)
        assertEquals("Post peeling", pl["nombre"]!!.jsonPrimitive.content)
        assertEquals("Sangrado que no para", pl["signosAlarma"]!!.jsonPrimitive.content)
        assertNull(pl["pacienteId"])
    }

    // ─── Parsers ───

    @Test fun filaYPlantillaDelApi() {
        val o = Json.parseToJsonElement(
            """{"id":"i1","paciente_id":"p1","fecha":"2026-10-15","titulo":"T","indicaciones":"a\nb","evitar":null,
               "signos_alarma":"Fiebre","control":"","visible_paciente":false,"cita_id":"c1","created_at":"x"}""",
        ).jsonObject
        val i = indicacionDeJson(o)!!
        assertEquals("a\nb", i.indicaciones); assertNull(i.evitar); assertNull(i.control)
        assertFalse(i.visiblePaciente); assertEquals("c1", i.citaId)
        assertTrue(indicacionDeJson(Json.parseToJsonElement("""{"id":"i2","titulo":"T"}""").jsonObject)!!.visiblePaciente)
        assertNull(indicacionDeJson(Json.parseToJsonElement("""{"titulo":"T"}""").jsonObject))
        val p = plantillaIndicacionesDeJson(Json.parseToJsonElement("""{"id":"x","nombre":"Post peeling","titulo":"Cuidados"}""").jsonObject)!!
        assertEquals("Cuidados", p.titulo)
    }

    @Test fun catalogoYSugerida() {
        val o = Json.parseToJsonElement(
            """{"rubro":"odontologia","rubros":["odontologia"],
               "plantillas":[{"clave":"extraccion-dental","nombre":"Cuidados después de una extracción dental",
                 "titulo":"Cuidados después de una extracción dental","rubros":["odontologia"],
                 "indicaciones":"Muerda la gasa 30 a 45 minutos\nComa blando","evitar":"Escupir","signos_alarma":"Fiebre","control":"Control en 7 días"},
                 {"nombre":"sin clave"}],
               "chips":{"indicaciones":["Muerda la gasa 30 minutos"],"evitar":["Usar sorbete",3],"signos_alarma":[],"control":["Retiro de puntos en 7 días"]},
               "sugerida":"extraccion-dental"}""",
        ).jsonObject
        val c = catalogoIndicacionesDeJson(o)!!
        assertEquals("odontologia", c.rubro)
        assertEquals(1, c.plantillas.size)
        assertEquals("Fiebre", c.plantillas[0].signosAlarma)
        assertEquals(listOf("Usar sorbete"), c.chips["evitar"])
        assertEquals("extraccion-dental", c.plantillaSugerida?.clave)
        assertEquals("Muerda la gasa 30 a 45 minutos\nComa blando", contenidoEditorDesde(c.plantillaSugerida).indicaciones)
        assertNull(catalogoIndicacionesDeJson(Json.parseToJsonElement("""{"error":"x"}""").jsonObject))
        val sinSug = catalogoIndicacionesDeJson(Json.parseToJsonElement("""{"plantillas":[],"sugerida":null}""").jsonObject)!!
        assertNull(sinSug.plantillaSugerida)
    }

    // ─── Chips y aprendidos ───

    @Test fun chipsBaseMasAprendidosRepetidosYAutocompletadoTodo() {
        val cat = catalogoIndicacionesDeJson(Json.parseToJsonElement(
            """{"plantillas":[],"chips":{"evitar":["Usar sorbete","Automedicarse"]}}""",
        ).jsonObject)
        // Objetos {texto, usos} y, de un servidor anterior, textos sueltos (1 uso).
        val sug = sugerenciasIndicacionesDeJson(Json.parseToJsonElement(
            """{"evitar":[{"texto":"Chicle por 24 horas","usos":3},{"texto":"Una sola vez escrito","usos":1},"Texto suelto",5,null]}""",
        ).jsonObject)
        assertEquals(3, sug.evitar.size)
        val f = frasesCampoIndicacion("evitar", cat, sug)
        assertTrue(f.porLinea)
        assertEquals(listOf("Chicle por 24 horas", "Una sola vez escrito"), f.opciones.take(2))
        val chips = f.chips.map { it.texto }
        assertTrue("Chicle por 24 horas" in chips)
        assertFalse("Una sola vez escrito" in chips)
        assertFalse("Texto suelto" in chips)
        assertTrue("Usar sorbete" in chips)
        assertTrue("Automedicarse" in chips)
        // Sin catálogo (sin red) ni aprendidos: vacío, nunca rompe.
        assertTrue(frasesCampoIndicacion("control", null, null).chips.isEmpty())
    }

    @Test fun busquedaSinTildes() {
        assertTrue(coincideBusquedaIndicacion("extraccion", "Cuidados después de una extracción dental"))
        assertTrue(coincideBusquedaIndicacion("", "x"))
        assertFalse(coincideBusquedaIndicacion("uña", "Peeling químico"))
        assertTrue(coincideBusquedaIndicacion("UNA", "Uña encarnada"))
    }

    // ─── Regla del aviso en la agenda ───

    private fun candidata(
        pacienteId: String? = "p1", pacientes: Boolean = false, sesiones: Boolean = true, encolada: Boolean = false,
        nutri: Boolean = false, receta: Boolean = false, medica: Boolean = false, otra: Boolean = false,
    ) = candidataIndicacionesTrasCompletar(pacienteId, pacientes, sesiones, encolada, nutri, receta, medica, otra)

    @Test fun reglaDeLaAgenda() {
        val sug = PlantillaBaseIndicaciones("peeling-quimico", "Después del peeling químico", "Después del peeling químico")
        assertTrue(ofrecerIndicacionesTrasCompletar(candidata(), sug))
        assertTrue(ofrecerIndicacionesTrasCompletar(candidata(pacientes = true, sesiones = false), sug))
        // El servicio no sugiere nada (sesión de rutina, consulta general): no avisa.
        assertFalse(ofrecerIndicacionesTrasCompletar(candidata(), null))
        assertFalse(candidata(pacienteId = null))
        assertFalse(candidata(pacientes = false, sesiones = false))
        // Sin señal: la cita quedó en la cola.
        assertFalse(candidata(encolada = true))
        // Nutrición (medidas → plan), receta / indicaciones médicas, consulta guiada.
        assertFalse(candidata(nutri = true))
        assertFalse(candidata(receta = true))
        assertFalse(candidata(medica = true))
        // Otra entrega en ese cierre (ejercicios, plan de tratamiento).
        assertFalse(candidata(otra = true))
    }

    @Test fun flujoDeLaFichaConsumeUnaVezYLaCitaSeOlvidaTrasGuardar() {
        val f = FlujoIndicacionesFicha.desde(AperturaIndicaciones("c1", "peeling-quimico", "e1", "Peeling químico"))
        assertTrue(f.abrirNuevo); assertEquals("c1", f.citaId)
        assertFalse(f.alCambiarTab("pagos").abrirNuevo)
        assertTrue(f.alCambiarTab("indicaciones").abrirNuevo)
        val consumido = f.consumido()
        assertFalse(consumido.abrirNuevo); assertEquals("c1", consumido.citaId)
        val guardado = consumido.trasGuardar()
        assertNull(guardado.citaId); assertNull(guardado.plantillaClave); assertEquals("e1", guardado.especialidadId)
        assertFalse(FlujoIndicacionesFicha.desde(null).abrirNuevo)
    }
}

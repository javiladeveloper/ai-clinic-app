package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Filas de Supabase → modelos de ejercicios de apoyo, y los cuerpos que se
 * mandan a POST /api/staff/ejercicios/plan (contrato: docs/app-contrato-ejercicios.md).
 */
class EjerciciosRepoParseoTest {

    private fun obj(json: String): JsonObject = Json.parseToJsonElement(json).jsonObject

    // ── Lecturas ──

    @Test
    fun leeUnEjercicioDeLaBiblioteca() {
        val e = aEjercicioBiblioteca(obj("""
            { "id": "e1", "slug": "puente", "nombre": "Puente de glúteos", "zona": "08_lumbar_core", "zona_nombre": "Lumbar y core",
              "zona_orden": 8, "objetivo": "fortalecimiento", "posicion": "boca_arriba", "materiales": ["colchoneta"],
              "condiciones": ["lumbalgia", "ciatica"], "dificultad": "basico", "pasos": ["Uno", "Dos"], "series": 3, "repeticiones": 10,
              "descanso_seg": 30, "dias_semana": 5, "gif_url": "https://x/a.gif", "postura_inicial_url": "https://x/i.png",
              "postura_final_url": null, "estado_revision": "aprobado", "clinica_id": null }
        """))!!
        assertEquals("Puente de glúteos", e.nombre)
        assertEquals(8, e.zonaOrden)
        assertEquals(listOf("lumbalgia", "ciatica"), e.condiciones)
        assertEquals(listOf("Uno", "Dos"), e.pasos)
        assertEquals("https://x/a.gif", e.gifUrl)
        assertNull(e.posturaFinalUrl)
        assertEquals("aprobado", e.estadoRevision)
        assertNull(e.clinicaId)
    }

    @Test
    fun unEjercicioIncompletoNoRompe() {
        // Arreglos en null y sin estado: listas vacías y "pendiente" (se marca "Sin revisar").
        val e = aEjercicioBiblioteca(obj("""{ "id": "e2", "nombre": "X", "materiales": null, "pasos": null }"""))!!
        assertTrue(e.materiales.isEmpty()); assertTrue(e.pasos.isEmpty()); assertTrue(e.condiciones.isEmpty())
        assertEquals("pendiente", e.estadoRevision)
        assertNull(aEjercicioBiblioteca(obj("""{ "nombre": "Sin id" }""")))
        assertNull(aEjercicioBiblioteca(obj("""{ "id": "e3" }""")))
    }

    private val filaPlan = """
        { "id": "p1", "paciente_id": "pac", "tratamiento_id": "t1", "sesion_id": "s4", "terapeuta_id": null, "momento": "sesion",
          "titulo": "Ejercicios de apoyo", "motivo": "Lumbalgia", "indicaciones_generales": "Por la mañana", "precauciones": null,
          "sesion": { "id": "s4", "numero": 4, "fecha": "2026-06-24" },
          "fecha_inicio": "2026-06-24", "fecha_fin": null, "estado": "activo", "visible_paciente": true, "token": null,
          "creado_por_nombre": "Lic. Ana", "created_at": "2026-06-24T15:00:00Z", "updated_at": "2026-06-24T15:00:00Z",
          "items": [
            { "id": "b", "plan_id": "p1", "ejercicio_id": "e2", "ejercicio_nombre": "Gato-camello", "orden": 2, "series": 2,
              "repeticiones": null, "sostener_seg": 20, "descanso_seg": 15, "veces_al_dia": 2, "dias": [1, 2, 3, 4, 5, 6, 7],
              "lado": null, "carga": null, "indicaciones": "Sin dolor", "dolor_maximo": 4, "activo": true,
              "created_at": "2026-06-24T15:00:01Z",
              "ejercicio": [ { "id": "e2", "slug": "gato", "nombre": "Gato-camello", "zona_nombre": "Lumbar y core", "objetivo": "movilidad",
                "posicion": "cuadrupedia", "materiales": ["colchoneta"], "pasos": ["Uno", "Dos"], "gif_url": "g.gif",
                "postura_inicial_url": "i.png", "postura_final_url": "f.png", "estado_revision": "pendiente" } ] },
            { "id": "a", "plan_id": "p1", "ejercicio_id": "e1", "ejercicio_nombre": "Puente", "orden": 1, "series": 3,
              "repeticiones": 10, "sostener_seg": null, "descanso_seg": 30, "veces_al_dia": 1, "dias": [1, 3, 5],
              "lado": "ambos", "carga": "Sin peso", "indicaciones": null, "dolor_maximo": null, "activo": true,
              "created_at": "2026-06-24T15:00:00Z", "ejercicio": null },
            { "id": "c", "plan_id": "p1", "ejercicio_id": "e3", "ejercicio_nombre": "Quitado", "orden": 3, "series": 1,
              "repeticiones": 1, "sostener_seg": null, "descanso_seg": null, "veces_al_dia": 1, "dias": null,
              "lado": null, "carga": null, "indicaciones": null, "dolor_maximo": null, "activo": false,
              "created_at": "2026-06-24T15:00:02Z", "ejercicio": null }
          ] }
    """

    @Test
    fun leeUnPlanConSuSesionYSusEjerciciosEnOrden() {
        val p = aPlanEjercicios(obj(filaPlan))!!
        assertEquals("sesion", p.momento)
        assertEquals(SesionDePlan("s4", 4, "2026-06-24"), p.sesion)
        assertEquals("Sesión #4 · 24/06/26", etiquetaPlan(p))
        assertEquals("Lic. Ana", p.creadoPorNombre)
        assertNull(p.token); assertNull(p.fechaFin); assertNull(p.precauciones)
        // Por `orden`; el quitado se conserva apagado (no cuenta entre los activos).
        assertEquals(listOf("a", "b", "c"), p.items.map { it.id })
        assertEquals(listOf("a", "b"), p.activos.map { it.id })
        val puente = p.items[0]
        assertEquals("3 series × 10 repeticiones", textoDosis(puente))
        assertEquals("Lun · Mié · Vie", textoDias(puente.dias))
        assertEquals("ambos", puente.lado); assertEquals("Sin peso", puente.carga)
        assertNull(puente.ejercicio)
        // El join del ejercicio llega como arreglo de uno (o como objeto): da igual.
        val gato = p.items[1]
        assertEquals("2 series × 20 s sostenido", textoDosis(gato))
        assertEquals(4, gato.dolorMaximo)
        assertEquals("g.gif", gato.ejercicio?.gifUrl)
        assertEquals(listOf("Uno", "Dos"), gato.ejercicio?.pasos)
        assertTrue(p.items[2].dias.isEmpty())
    }

    @Test
    fun elPlanDeAltaNoTieneSesion() {
        val p = aPlanEjercicios(obj("""{ "id": "p2", "tratamiento_id": "t1", "momento": "alta", "sesion": null, "estado": "finalizado",
            "fecha_fin": "2026-07-01", "visible_paciente": false, "token": "abc", "items": [] }"""))!!
        assertEquals("alta", p.momento)
        assertNull(p.sesion)
        assertEquals("Al terminar el tratamiento", etiquetaPlan(p))
        assertFalse(p.visiblePaciente)
        assertEquals("abc", p.token)
        assertEquals("2026-07-01", p.fechaFin)
        // Un `momento` desconocido (o ausente) se lee como 'sesion', igual que la web.
        assertEquals("sesion", aPlanEjercicios(obj("""{ "id": "p3", "momento": "otro" }"""))!!.momento)
        assertNull(aPlanEjercicios(obj("""{ "titulo": "sin id" }""")))
    }

    @Test
    fun losVigentesVanArribaYElRestoDelMasRecienteAlMasViejo() {
        fun p(id: String, estado: String, creado: String) = PlanEjercicios(id = id, estado = estado, createdAt = creado)
        val orden = ordenarPlanes(listOf(
            p("viejo", "finalizado", "2026-06-01T00:00:00Z"), p("pausado", "pausado", "2026-06-20T00:00:00Z"),
            p("vigente", "activo", "2026-06-10T00:00:00Z"),
        ))
        assertEquals(listOf("vigente", "pausado", "viejo"), orden.map { it.id })
    }

    @Test
    fun leeLosRegistrosYLasSesiones() {
        val r = aRegistroEjercicio(obj("""{ "item_id": "a", "fecha": "2026-06-25", "completado": true, "dolor": 2, "comentario": null }"""))!!
        assertEquals(RegistroEjercicio("a", "2026-06-25", true, 2, null), r)
        assertNull(aRegistroEjercicio(obj("""{ "fecha": "2026-06-25", "completado": true }""")))
        val s = aSesionDestino(obj("""{ "id": "s4", "numero": 4, "fecha": "2026-06-24", "estado": "Completada", "tratamiento_id": "t1" }"""))!!
        assertEquals(SesionDestino("s4", 4, "2026-06-24", "Completada", "t1"), s)
    }

    // ── Escrituras ──

    private val enSesion = DestinoEjercicios("sesion", "s4", "t1")

    @Test
    fun ningunCuerpoLlevaClinicaId() {
        val dosis = DosisEdicion("3", "10", "", "30", 1, listOf(1, 3, 5), null, "", null, "")
        val cuerpos = listOf(
            cuerpoAgregar("pac", listOf("e1"), enSesion), cuerpoCopiar("pac", "p0", enSesion), cuerpoEditarItem("i1", dosis),
            cuerpoQuitarItem("i1"), cuerpoEditarPlan("p1", "T", "", "", "", true), cuerpoDePlan("estado", "p1", "pausado"),
            cuerpoDePlan("compartir", "p1"), cuerpoDePlan("revocar", "p1"),
        )
        assertEquals(
            listOf("agregar", "copiar", "editarItem", "quitarItem", "editarPlan", "estado", "compartir", "revocar"),
            cuerpos.map { (it["accion"] as JsonPrimitive).content },
        )
        cuerpos.forEach { c -> assertFalse(c.keys.any { it == "clinica_id" || it == "clinicaId" }, "clinica_id en $c") }
    }

    @Test
    fun elDestinoViajaEnAgregarYCopiar() {
        val c = cuerpoAgregar("pac", listOf("e1", "e2", "e1"), enSesion)
        assertEquals("pac", (c["pacienteId"] as JsonPrimitive).content)
        assertEquals(listOf("e1", "e2"), (c["ejercicioIds"] as JsonArray).map { (it as JsonPrimitive).content })
        assertEquals("sesion", (c["momento"] as JsonPrimitive).content)
        assertEquals("s4", (c["sesionId"] as JsonPrimitive).content)
        // "Antes de la primera sesión": sin sesión, con el tratamiento.
        val antes = cuerpoAgregar("pac", listOf("e1"), DestinoEjercicios("sesion", null, "t1"))
        assertFalse("sesionId" in antes)
        assertEquals("t1", (antes["tratamientoId"] as JsonPrimitive).content)
        // Al terminar, sin tratamiento ("para casa").
        val casa = cuerpoCopiar("pac", "p0", DestinoEjercicios("alta", null, null))
        assertEquals("alta", (casa["momento"] as JsonPrimitive).content)
        assertEquals("p0", (casa["desdePlanId"] as JsonPrimitive).content)
        assertFalse("sesionId" in casa); assertFalse("tratamientoId" in casa)
    }

    @Test
    fun ajustarMandaNumerosYVaciosComoNull() {
        val c = cuerpoEditarItem("i1", DosisEdicion(
            series = " 4 ", repeticiones = "", sostenerSeg = "20", descansoSeg = "", vecesAlDia = 2, dias = listOf(5, 1, 1, 3),
            lado = "derecho", carga = "Banda roja", dolorMaximo = 4, indicaciones = "Despacio",
        ))
        assertEquals(JsonPrimitive(4), c["series"])
        assertEquals(JsonNull, c["repeticiones"])
        assertEquals(JsonPrimitive(20), c["sostenerSeg"])
        assertEquals(JsonNull, c["descansoSeg"])
        assertEquals(JsonPrimitive(2), c["vecesAlDia"])
        assertEquals(listOf(1, 3, 5), (c["dias"] as JsonArray).map { (it as JsonPrimitive).content.toInt() })
        assertEquals(JsonPrimitive("derecho"), c["lado"])
        assertEquals(JsonPrimitive(4), c["dolorMaximo"])
        // Sin lado y sin tope: null (el servidor los limpia). Lo que no es número viaja tal cual: valida el servidor.
        val sin = cuerpoEditarItem("i1", DosisEdicion("x", "10", "", "", 1, listOf(1), null, "", null, ""))
        assertEquals(JsonNull, sin["lado"]); assertEquals(JsonNull, sin["dolorMaximo"])
        assertEquals(JsonPrimitive("x"), sin["series"])
    }

    @Test
    fun elEnlaceSaleDelTokenDeCompartir() {
        val token = "a".repeat(64)
        assertEquals(token, tokenDeRespuesta(obj("""{ "ok": true, "planId": "p1", "token": "$token" }""")))
        assertNull(tokenDeRespuesta(obj("""{ "ok": true, "planId": "p1", "token": null }""")))
        assertNull(tokenDeRespuesta(null))
        assertEquals("https://www.saniape.com/ejercicios/$token", urlPlanEjercicios(token, "https://www.saniape.com/"))
    }
}

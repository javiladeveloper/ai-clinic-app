package pe.saniape.app.data.staff

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Rutinas de ejercicios: gemelo de lib/ejercicios-rutinas.ts (contrato §5). */
class EjerciciosRutinasTest {

    private val clinica = """
        { "items": [
          { "id": "r1", "clinica_id": "c1", "nombre": "Lumbalgia — fase subaguda", "descripcion": "Dolor lumbar", "indicaciones_generales": null,
            "precauciones": null, "created_at": "x", "updated_at": "x",
            "ejercicios": [
              { "ejercicio_id": "e1", "slug": "puente", "ejercicio_nombre": "Puente de glúteos", "series": 3, "repeticiones": 10,
                "sostener_seg": null, "descanso_seg": 30, "veces_al_dia": 1, "dias": [5, 1, 3], "lado": null, "carga": null,
                "indicaciones": null, "dolor_maximo": 4 },
              { "slug": "roto" }
            ] },
          { "id": null, "nombre": "Sin id" }
        ] }
    """.trimIndent()

    private val base = """
        { "items": [
          { "id": "hombro", "nombre": "Hombro doloroso", "descripcion": "Subacromial", "motivo": "Hombro", "indicacionesGenerales": "…",
            "precauciones": "…", "palabras": ["manguito", "supraespinoso"],
            "ejercicios": [
              { "slug": "pendulo", "ejercicioId": "e9", "nombre": "Péndulo de Codman", "disponible": true, "posturaInicialUrl": null,
                "gifUrl": null, "series": 2, "repeticiones": 10, "sostenerSeg": null, "descansoSeg": 30, "vecesAlDia": 2,
                "dias": [1,2,3,4,5,6,7], "lado": "ambos", "carga": null, "indicaciones": null, "dolorMaximo": null, "texto": "…" },
              { "slug": "viejo", "ejercicioId": null, "nombre": "Ejercicio retirado", "disponible": false, "series": 2, "dias": [1] }
            ] }
        ] }
    """.trimIndent()

    @Test
    fun parseaLasDeLaClinica() {
        val r = parsearRutinasClinica(clinica)
        assertNotNull(r)
        assertEquals(1, r.size)
        val x = r[0]
        assertEquals(ORIGEN_CLINICA, x.origen)
        assertTrue(x.esDeClinica)
        assertEquals("clinica:r1", x.clave)
        assertEquals(1, x.ejercicios.size) // el malformado se descarta
        val e = x.ejercicios[0]
        assertEquals(listOf(1, 3, 5), e.dias)
        assertEquals(4, e.dolorMaximo)
        assertEquals("3 series × 10 repeticiones · Lun · Mié · Vie", textoItemRutina(e))
    }

    @Test
    fun parseaLasBaseConLasQueYaNoEstan() {
        val r = parsearRutinasBase(base)
        assertNotNull(r)
        val h = r.single()
        assertEquals(ORIGEN_BASE, h.origen)
        assertFalse(h.esDeClinica)
        assertEquals(listOf("manguito", "supraespinoso"), h.palabras)
        assertEquals(listOf("Ejercicio retirado"), noDisponibles(h))
        assertEquals("2 series × 10 repeticiones · Todos los días · 2 veces al día", textoItemRutina(h.ejercicios[0]))
    }

    @Test
    fun respuestaInesperadaEsNull() {
        assertNull(parsearRutinasClinica("<!doctype html>404"))
        assertNull(parsearRutinasBase("""{ "error": "x" }"""))
        assertEquals(emptyList(), parsearRutinasClinica("""{ "items": [] }"""))
    }

    @Test
    fun buscaSinTildesTodasLasPalabrasYLasDeLaClinicaPrimero() {
        val todas = parsearRutinasBase(base)!! + parsearRutinasClinica(clinica)!!
        assertEquals(listOf("clinica:r1", "base:hombro"), buscarRutinas(todas, "").map { it.clave })
        assertEquals(listOf("clinica:r1"), buscarRutinas(todas, "GLUTEOS puente").map { it.clave })
        assertEquals(listOf("base:hombro"), buscarRutinas(todas, "supraespinoso").map { it.clave })
        assertEquals(listOf("base:hombro"), buscarRutinas(todas, "codman").map { it.clave }) // por ejercicio
        assertEquals(emptyList(), buscarRutinas(todas, "hombro lumbar").map { it.clave })
    }

    @Test
    fun resumen() {
        val r = parsearRutinasBase(base)!!.single()
        assertEquals("2 ejercicios · Péndulo de Codman, Ejercicio retirado", resumenRutina(r))
        assertEquals("2 ejercicios · Péndulo de Codman…", resumenRutina(r, max = 1))
    }

    @Test
    fun mismoNombreSinImportarMayusculasNiEspacios() {
        val lista = parsearRutinasClinica(clinica)!! + parsearRutinasBase(base)!!
        assertEquals("lumbalgia — fase subaguda", claveNombreRutina("  Lumbalgia   —  FASE subaguda "))
        assertEquals("r1", rutinaConNombre(lista, " lumbalgia —   fase SUBAGUDA")?.id)
        assertNull(rutinaConNombre(lista, "Hombro doloroso")) // las base no se reemplazan
        assertNull(rutinaConNombre(lista, "   "))
    }

    @Test
    fun reemplazarPideConfirmacionAntes() {
        assertEquals(PasoGuardarRutina.GUARDAR, pasoGuardarRutina(existe = false, confirmado = false))
        assertEquals(PasoGuardarRutina.GUARDAR, pasoGuardarRutina(existe = false, confirmado = true))
        assertEquals(PasoGuardarRutina.PEDIR_CONFIRMACION, pasoGuardarRutina(existe = true, confirmado = false))
        assertEquals(PasoGuardarRutina.REEMPLAZAR, pasoGuardarRutina(existe = true, confirmado = true))
        assertEquals("Guardar rutina", textoBotonGuardarRutina(PasoGuardarRutina.GUARDAR, false))
        assertEquals("Reemplazar rutina", textoBotonGuardarRutina(PasoGuardarRutina.PEDIR_CONFIRMACION, false))
        assertEquals("Sí, reemplazar", textoBotonGuardarRutina(PasoGuardarRutina.REEMPLAZAR, false))
        assertEquals("Guardando…", textoBotonGuardarRutina(PasoGuardarRutina.REEMPLAZAR, true))
    }

    @Test
    fun nombreSugerido() {
        assertEquals("Lumbalgia", nombreSugeridoRutina(" Lumbalgia ", "Plan X"))
        assertEquals("Plan X", nombreSugeridoRutina(null, "Plan X"))
        assertEquals("", nombreSugeridoRutina("", "Ejercicios de apoyo"))
        assertEquals(80, nombreSugeridoRutina("x".repeat(200), null).length)
    }

    @Test
    fun cuerpoDeUsarSinClinicaId() {
        val d = DestinoEjercicios("sesion", "s1", "t1")
        val cl = cuerpoUsarRutina("p1", parsearRutinasClinica(clinica)!!.single(), d)
        assertEquals("rutina", cl["accion"]?.let { (it as JsonPrimitive).content })
        assertEquals("r1", (cl["plantillaId"] as JsonPrimitive).content)
        assertNull(cl["rutinaBase"])
        assertEquals("s1", (cl["sesionId"] as JsonPrimitive).content)
        assertEquals("t1", (cl["tratamientoId"] as JsonPrimitive).content)
        assertFalse(cl.containsKey("clinica_id"))
        val b = cuerpoUsarRutina("p1", parsearRutinasBase(base)!!.single(), DestinoEjercicios("alta", null, "t1"))
        assertEquals("hombro", (b["rutinaBase"] as JsonPrimitive).content)
        assertNull(b["plantillaId"])
        assertNull(b["sesionId"])
        assertEquals("alta", (b["momento"] as JsonPrimitive).content)
    }

    @Test
    fun cuerpoDeGuardar() {
        val c = cuerpoGuardarRutina(" Lumbalgia ", "", "plan1", "Respira", " ")
        assertEquals("Lumbalgia", (c["nombre"] as JsonPrimitive).content)
        assertEquals("plan1", (c["desdePlanId"] as JsonPrimitive).content)
        assertEquals("Respira", (c["indicacionesGenerales"] as JsonPrimitive).content)
        assertEquals(JsonNull, c["precauciones"])
        assertFalse(c.containsKey("clinica_id"))
    }

    @Test
    fun respuestaDeUsarYAvisoConOmitidos() {
        val ok = rutinaAplicadaDe(Json.parseToJsonElement("""{ "ok": true, "planId": "p", "agregados": 5, "omitidos": ["Viejo", "Otro"] }""").jsonObject)
        assertEquals(RutinaAplicada(5, listOf("Viejo", "Otro")), ok)
        val (t, d) = avisoRutinaAplicada("Hombro", ok)
        assertEquals("«Hombro»: 5 ejercicios agregados", t)
        assertEquals("Ya no están en la biblioteca: Viejo, Otro.", d)
        val (t0, d0) = avisoRutinaAplicada("Hombro", RutinaAplicada(0, emptyList()))
        assertEquals("Esos ejercicios ya estaban en el plan", t0)
        assertEquals("Con la dosis de la rutina. Ajusta cada uno a este paciente.", d0)
        assertEquals("«Hombro»: 1 ejercicio agregado", avisoRutinaAplicada("Hombro", RutinaAplicada(1, emptyList())).first)
        assertEquals(RutinaAplicada(0, emptyList()), rutinaAplicadaDe(null))
    }

    @Test
    fun servidorViejoOModuloApagadoOcultan() {
        assertTrue(funcionNoDisponible(404, null, cuerpoEsJsonConError = false))
        assertFalse(funcionNoDisponible(404, "NO_ENCONTRADO", cuerpoEsJsonConError = true))
        assertTrue(funcionNoDisponible(403, "MODULO_APAGADO", cuerpoEsJsonConError = true))
        assertFalse(funcionNoDisponible(403, "SIN_PERMISO", cuerpoEsJsonConError = true))
        assertFalse(funcionNoDisponible(500, null, cuerpoEsJsonConError = false))
    }
}

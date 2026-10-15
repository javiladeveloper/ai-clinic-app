package pe.saniape.app.data.staff

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Estado del autocompletado abierto: lo que aprende la evaluación psicológica
 * al cerrar (aprender() del ViewModel), la caché con vencimiento e
 * invalidación, la deduplicación de envíos y el filtro de pictogramas.
 */
class FrasesCampoEstadoTest {

    private fun ev(motivo: String = "", conclusiones: String = "", area: String = "") = EvaluacionPsico(
        id = "e1", pacienteId = "22222222-2222-2222-2222-222222222222",
        entrevista = EntrevistaPsico(mapOf("motivo" to motivo)),
        analisis = AnalisisPsico(areas = mapOf("emocional" to area), conclusiones = conclusiones),
    )

    // ── aprender() de la evaluación psicológica ──

    @Test fun salirYLuegoOnClearedNoInflan() {
        val a = AprendizajePantalla()
        a.iniciar(textosPsicoAprendibles(ev(motivo = "Ansiedad")), puedeAprender = true)
        val ahora = textosPsicoAprendibles(ev(motivo = "Ansiedad, insomnio", area = "Indicadores de ansiedad"))
        // salir(): viajan solo los dos campos que cambiaron, con lo que tenían.
        val primero = a.tomarCambios(ahora)
        assertEquals(
            setOf(
                CambioFrase(ClaveFrase.PSICO_MOTIVO, "Ansiedad, insomnio", "Ansiedad"),
                CambioFrase("psico.eval.area.emocional", "Indicadores de ansiedad", ""),
            ),
            primero.toSet(),
        )
        // onCleared() justo después: nada.
        assertEquals(emptyList(), a.tomarCambios(ahora))
        // Escribir más y cambiar de pestaña: solo lo nuevo, contra lo último aprendido.
        val despues = textosPsicoAprendibles(ev(motivo = "Ansiedad, insomnio", area = "Indicadores de ansiedad", conclusiones = "Se sugiere psicoterapia"))
        assertEquals(listOf(CambioFrase(ClaveFrase.PSICO_CONCLUSIONES, "Se sugiere psicoterapia", "")), a.tomarCambios(despues))
    }

    @Test fun loQueTeniaAlAbrirNoSeAprendeYUnReintentoNoLoPisa() {
        val a = AprendizajePantalla()
        val alAbrir = textosPsicoAprendibles(ev(motivo = "Ansiedad"))
        a.iniciar(alAbrir, puedeAprender = true)
        // "Reintentar" recarga con lo ya escrito: no cambia la línea base.
        a.iniciar(textosPsicoAprendibles(ev(motivo = "Ansiedad, insomnio")), puedeAprender = true)
        assertEquals(emptyList(), a.tomarCambios(alAbrir))
    }

    @Test fun sinCargaOEnSoloLecturaNoSeAprende() {
        val sinCarga = AprendizajePantalla()
        assertEquals(emptyList(), sinCarga.tomarCambios(textosPsicoAprendibles(ev(motivo = "Ansiedad"))))
        val cerrada = AprendizajePantalla()
        cerrada.iniciar(textosPsicoAprendibles(ev()), puedeAprender = false)
        assertEquals(emptyList(), cerrada.tomarCambios(textosPsicoAprendibles(ev(motivo = "Ansiedad"))))
    }

    // ── Caché con vencimiento ──

    private val trico = ClaveCacheFrases("cli", listOf(ClaveFrase.CAPILAR_OBSERVACIONES), null, null)
    private val estetica = ClaveCacheFrases("cli", CAMPOS_APLICACION_ESTETICA.sorted(), null, null)
    private fun resp(campo: String) = mapOf(campo to RespuestaFrasesCampo(campo, true, listOf("Base"), emptyList()))

    @Test fun laCacheVenceYSeVuelveAPedir() {
        var t = 1_000L
        val c = CacheFrases(ttlMs = 600_000L) { t }
        c.guardar(trico, resp(ClaveFrase.CAPILAR_OBSERVACIONES))
        assertNotNull(c.leer(trico))
        t += 599_999L
        assertNotNull(c.leer(trico))
        // A los 10 min: lo que aprendió el servidor (tricoscopía) llega en la próxima apertura.
        t += 1L
        assertNull(c.leer(trico))
    }

    @Test fun invalidarTrasGuardarSoloBorraLasLecturasDeEsosCampos() {
        val c = CacheFrases { 0L }
        c.guardar(trico, resp(ClaveFrase.CAPILAR_OBSERVACIONES))
        c.guardar(estetica, resp(ClaveFrase.ESTETICA_OBSERVACIONES))
        c.invalidar(listOf(ClaveFrase.CAPILAR_OBSERVACIONES))
        assertNull(c.leer(trico))
        assertNotNull(c.leer(estetica))
        // Un campo de la lectura basta (notas de la zona → todo el editor de estética).
        c.invalidar(listOf(ClaveFrase.ESTETICA_NOTAS_ZONA))
        assertNull(c.leer(estetica))
        // Otra clínica nunca comparte entrada.
        c.guardar(trico, resp(ClaveFrase.CAPILAR_OBSERVACIONES))
        assertNull(c.leer(trico.copy(clinica = "otra")))
        c.limpiar()
        assertNull(c.leer(trico))
    }

    // ── Deduplicación de envíos ──

    @Test fun elMismoPostViajaUnaVezYSeLiberaSiFalla() {
        val e = EnviadosFrases(max = 3)
        assertTrue(e.marcar("a"))
        assertFalse(e.marcar("a"))
        e.liberar("a")
        assertTrue(e.marcar("a"))
        assertTrue(e.marcar("b")); assertTrue(e.marcar("c"))
        // Lleno: se vacía y sigue funcionando (nunca crece sin límite).
        assertTrue(e.marcar("d"))
        assertTrue(e.marcar("a"))
    }

    // ── Pictogramas (cerca de \p{Extended_Pictographic}) ──

    @Test fun pictogramasDelPlanoBasicoNoSeAprenden() {
        val c = ClaveFrase.TRATAMIENTO_INDICACIONES
        for (s in listOf("Marca © registrada", "Producto ® original", "Calidad ™ premium", "Usar reloj ⌚ diario", "Ir ↔ volver", "Mejoría ⭐ notable")) {
            assertFalse(esFraseRegistrable(s, c), s)
        }
        // Tildes, ñ, ¿¡ y la flecha simple siguen pasando.
        assertTrue(esFraseRegistrable("¿Dolor? Aplicar compresa → reposo", c))
        assertTrue(esFraseRegistrable("Niño con señal de mejoría", c))
    }
}

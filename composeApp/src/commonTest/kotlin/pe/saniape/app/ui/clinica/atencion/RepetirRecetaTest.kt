package pe.saniape.app.ui.clinica.atencion

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import pe.saniape.app.data.staff.ItemRecetaPrevia

/**
 * «↻ Repetir receta» (🗒 La última vez): los medicamentos anteriores pasan como
 * filas EDITABLES del diálogo de hoy; no se emite nada solo. El servidor sigue
 * validando (colegiatura, controlados, receta especial) al emitir.
 */
class RepetirRecetaTest {

    private val sertralina = ItemRecetaPrevia(
        dci = "Sertralina", concentracion = "50 mg", forma = "Tableta", via = "Oral",
        dosis = "1 tableta", frecuencia = "cada 24 horas", duracion = "30 días", cantidad = 30.0, unidad = "tabletas",
    )

    @Test
    fun copiaLosCamposYLaCantidadComoTexto() {
        val filas = itemsParaRepetir(listOf(sertralina, sertralina.copy(dci = "Clonazepam", cantidad = 7.5), ItemRecetaPrevia(dci = " ")))
        assertEquals(listOf("Sertralina", "Clonazepam"), filas.map { it.dci })
        assertEquals("30", filas[0].cantidad)
        assertEquals("7.5", filas[1].cantidad)
        assertEquals("1 tableta", filas[0].dosis)
        assertEquals("tabletas", filas[0].unidad)
        // Filas distintas (uid propio): quitar una no le pasa su estado a la otra.
        assertTrue(filas[0].uid != filas[1].uid)
    }

    @Test
    fun sinCantidadQuedaVaciaParaEscribirla() {
        assertEquals("", itemsParaRepetir(listOf(sertralina.copy(cantidad = 0.0)))[0].cantidad)
    }

    @Test
    fun respetaElTopeDeLaReceta() {
        assertEquals(MAX_ITEMS_RECETA, itemsParaRepetir(List(20) { sertralina }).size)
    }

    @Test
    fun sinMedicamentos_nada() {
        assertTrue(itemsParaRepetir(emptyList()).isEmpty())
    }
}

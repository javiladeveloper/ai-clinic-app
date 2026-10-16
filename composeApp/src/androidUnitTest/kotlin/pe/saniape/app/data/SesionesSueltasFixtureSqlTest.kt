package pe.saniape.app.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Lee el fixture SQL REAL de la web (supabase/fixes/sueltas-prueba-gemelo.sql) y
 * comprueba que la snapshot de SesionesSueltasFixture.kt tiene los mismos casos
 * y que la regla Kotlin da lo mismo en cada uno. Si la web agrega o cambia un
 * caso, esto falla: regenera la snapshot (ver la cabecera de SesionesSueltasFixture.kt).
 *
 * Busca el repo web en $SANIA_WEB_DIR o al lado (../../ai-clinic-dashboard desde
 * composeApp). Si no está (CI de la app), se salta con un aviso: la snapshot y
 * sus 31 casos se prueban igual en SesionesSueltasTest (commonTest).
 */
class SesionesSueltasFixtureSqlTest {

    private fun fixtureSql(): File? {
        val rel = "supabase/fixes/sueltas-prueba-gemelo.sql"
        val candidatos = listOfNotNull(
            System.getenv("SANIA_WEB_DIR")?.let { File(it, rel) },
            File("../../ai-clinic-dashboard/$rel"),
            File("../ai-clinic-dashboard/$rel"),
        )
        return candidatos.firstOrNull { it.isFile }
    }

    private fun num(x: String): Double? = if (x.startsWith("NULL")) null else x.toDouble()

    private fun JsonObject.s(k: String): String? = (this[k] as? JsonPrimitive)?.content?.takeIf { it != "null" }

    @Test
    fun la_snapshot_coincide_con_el_sql_de_la_web() {
        val f = fixtureSql()
        if (f == null) {
            println("SesionesSueltasFixtureSqlTest: sin repo web al lado, se salta (usa SANIA_WEB_DIR).")
            return
        }
        val sql = f.readText().replace("\r\n", "\n")
        // Las mismas expresiones que lib/__tests__/sueltas-acordado.test.ts.
        val r1 = Regex("""^ {2}\('((?:[^']|'')*)', (\S+), (\S+), (\S+), (\S+), '(.*)'::jsonb, (?:'([0-9a-f-]+)'::uuid|NULL::uuid), (\S+)\)[,;]?$""", RegexOption.MULTILINE)
        val r2 = Regex("""^ {2}\('((?:[^']|'')*)', '([^']*)', (\S+), (\S+), (\S+), (\S+), (\S+)\)[,;]?$""", RegexOption.MULTILINE)
        val casos = r1.findAll(sql).toList()
        val costos = r2.findAll(sql).toList()
        assertEquals(CASOS_ACORDADO_FIXTURE.size, casos.size, "casos de acordado_suelta: regenera la snapshot")
        assertEquals(CASOS_COSTO_FIXTURE.size, costos.size, "casos de costo_sesion_al_cobrar: regenera la snapshot")
        assertTrue(casos.size >= 25 && costos.size >= 5)

        casos.forEachIndexed { i, m ->
            val g = m.groupValues
            val nombre = g[1].replace("''", "'")
            assertEquals(CASOS_ACORDADO_FIXTURE[i].nombre, nombre, "orden/nombre del caso $i")
            val t = TratPago(
                modalidad = "Sesión suelta", precioAcordado = num(g[3]), precioPorSesion = num(g[4]),
                totalSesiones = num(g[5])?.toInt(),
            )
            assertEquals(num(g[2]), montoAcordado(t), "pactado «$nombre»")
            val filas = Json.parseToJsonElement(g[6]).jsonArray.map { it as JsonObject }
            val sesiones = filas.map {
                SesionDevengo(it.s("id"), it.s("estado"), it.s("costo")?.toDouble(), it.s("numero")?.toInt(), it.s("fecha"))
            }
            val pagos = filas.filter { it.s("pagada") == "true" }.map { PagoDevengo(it.s("id"), 1.0) }
            val incluir = g[7].ifEmpty { null }
            assertEquals(g[8].toDouble(), montoAcordado(t, ContextoSesiones(sesiones, pagos, incluir)), "acordado «$nombre»")
        }
        costos.forEach { m ->
            val g = m.groupValues
            val t = TratPago(modalidad = g[2], precioAcordado = num(g[3]), precioPorSesion = num(g[4]), totalSesiones = num(g[5])?.toInt())
            assertEquals(g[7].toDouble(), costoSesionTrasCobro(t, null, g[6].toDouble()), "costo «${g[1]}»")
        }
    }
}

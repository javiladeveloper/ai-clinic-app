package pe.saniape.app.data

import io.github.jan.supabase.auth.auth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import pe.saniape.app.data.offline.CacheLectura
import pe.saniape.app.data.staff.ClasificacionCrecimiento
import pe.saniape.app.data.staff.DIAS_POR_MES
import pe.saniape.app.data.staff.EstadoDosis
import pe.saniape.app.data.staff.IndicadorCrecimiento
import pe.saniape.app.data.staff.NivelCrecimiento
import pe.saniape.app.data.staff.PuntoPaciente
import pe.saniape.app.data.staff.VentanaCurva
import pe.saniape.app.data.staff.sexoValido
import pe.saniape.app.data.staff.ventanaCurva

// ─── Modelo (contrato: docs/app-contrato-portal-paciente.md §11 y §12 de la web) ──

data class ClinicaNinoPortal(val nombre: String, val logoUrl: String?, val colorPrincipal: String?)

/** Una medición del niño (sin notas: no vienen). */
data class MedicionNinoPortal(
    val fecha: String, val edadDias: Int?, val peso: Double?, val talla: Double?, val perimetroCefalico: Double?, val imc: Double?,
)

/** Un indicador de la última medición, ya calculado por el servidor. */
data class IndicadorNinoPortal(
    val indicador: IndicadorCrecimiento, val sigla: String, val nombre: String, val valor: Double, val unidad: String,
    val z: Double, val percentil: Double, val clasificacion: ClasificacionCrecimiento?,
)

data class UltimaNinoPortal(val fecha: String, val edadTexto: String, val indicadores: List<IndicadorNinoPortal>)

data class NinoCrecimientoPortal(
    val pacienteId: String,
    val nombre: String,
    /** "F" / "M" / null (sin sexo no hay curva). */
    val sexo: String?,
    val fechaNacimiento: String,
    val clinica: ClinicaNinoPortal?,
    /** Cronológicas. */
    val mediciones: List<MedicionNinoPortal>,
    val ultima: UltimaNinoPortal?,
)

data class MiCrecimiento(val ninos: List<NinoCrecimientoPortal>) {
    val tieneDatos: Boolean get() = ninos.isNotEmpty()

    companion object {
        val VACIO = MiCrecimiento(emptyList())
    }
}

data class DosisNinoPortal(
    val id: String, val grupo: String, val vacuna: String, val dosis: String,
    val estado: EstadoDosis, val estadoTexto: String, val fechaRecomendada: String?,
    val fechaAplicacion: String?, val lote: String?, val lugar: String?,
)

data class OtraVacunaPortal(val vacuna: String, val dosis: String, val fechaAplicacion: String, val lote: String?, val lugar: String?)

data class ResumenVacunasPortal(val texto: String, val aplicadas: Int, val total: Int, val atrasadas: Int, val pendientes: Int, val alDia: Boolean)

data class NinoVacunasPortal(
    val pacienteId: String,
    val nombre: String,
    val fechaNacimiento: String,
    val clinica: ClinicaNinoPortal?,
    val resumen: ResumenVacunasPortal,
    /** En el orden del carné. */
    val dosis: List<DosisNinoPortal>,
    val otras: List<OtraVacunaPortal>,
) {
    /** Lo que le toca ahora (atrasadas, le toca y próximas), como la web. */
    val ahora: List<DosisNinoPortal>
        get() = dosis.filter { it.estado == EstadoDosis.ATRASADA || it.estado == EstadoDosis.PENDIENTE || it.estado == EstadoDosis.PROXIMA }

    /** Las columnas del carné, en orden. */
    val grupos: List<String> get() = dosis.map { it.grupo }.distinct()
}

data class MisVacunas(val ninos: List<NinoVacunasPortal>) {
    val tieneDatos: Boolean get() = ninos.isNotEmpty()

    companion object {
        val VACIO = MisVacunas(emptyList())
    }
}

// ─── Parseo (tolerante) ──────────────────────────────────────────────────────

private fun JsonObject.txtPed(k: String): String? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() && it != "null" }
private fun JsonObject.numPed(k: String): Double? =
    (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.toDoubleOrNull()?.takeIf { it.isFinite() }
private fun JsonObject.posPed(k: String): Double? = numPed(k)?.takeIf { it > 0 }
private val FECHA_PED = Regex("""^\d{4}-\d{2}-\d{2}$""")

private fun clinicaNino(o: JsonObject): ClinicaNinoPortal? = (o["clinica"] as? JsonObject)?.let { c ->
    c.txtPed("nombre")?.let { ClinicaNinoPortal(it, c.txtPed("logo_url"), c.txtPed("color_principal")) }
}

/**
 * JSON de /api/paciente/mi-crecimiento → modelo. Tolerante: niños sin id,
 * nombre, fecha de nacimiento o mediciones se saltan; indicadores desconocidos
 * se descartan; las mediciones se reordenan cronológicamente. Null = no es JSON.
 */
fun parsearMiCrecimiento(texto: String): MiCrecimiento? {
    val raiz = runCatching { Json.parseToJsonElement(texto).jsonObject }.getOrNull() ?: return null
    val ninos = (raiz["ninos"] as? JsonArray).orEmpty().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val id = o.txtPed("pacienteId") ?: return@mapNotNull null
        val nac = o.txtPed("fechaNacimiento")?.take(10)?.takeIf { FECHA_PED.matches(it) } ?: return@mapNotNull null
        val meds = (o["mediciones"] as? JsonArray).orEmpty().mapNotNull { mEl ->
            val m = mEl as? JsonObject ?: return@mapNotNull null
            val fecha = m.txtPed("fecha")?.take(10)?.takeIf { FECHA_PED.matches(it) } ?: return@mapNotNull null
            val med = MedicionNinoPortal(
                fecha, m.numPed("edadDias")?.toInt(), m.posPed("peso"), m.posPed("talla"), m.posPed("perimetroCefalico"), m.posPed("imc"),
            )
            if (med.peso == null && med.talla == null && med.perimetroCefalico == null) null else med
        }.sortedBy { it.fecha }
        if (meds.isEmpty()) return@mapNotNull null
        val ult = (o["ultima"] as? JsonObject)?.let { u ->
            val inds = (u["indicadores"] as? JsonArray).orEmpty().mapNotNull { iEl ->
                val i = iEl as? JsonObject ?: return@mapNotNull null
                val ind = IndicadorCrecimiento.de(i.txtPed("indicador")) ?: return@mapNotNull null
                val valor = i.numPed("valor") ?: return@mapNotNull null
                val z = i.numPed("z") ?: return@mapNotNull null
                val cl = (i["clasificacion"] as? JsonObject)?.let { c ->
                    val t = c.txtPed("texto"); val n = NivelCrecimiento.de(c.txtPed("nivel"))
                    if (t != null && n != null) ClasificacionCrecimiento(t, n) else null
                }
                IndicadorNinoPortal(ind, i.txtPed("sigla") ?: ind.sigla, i.txtPed("nombre") ?: ind.nombre, valor,
                    i.txtPed("unidad") ?: ind.unidad, z, i.numPed("percentil") ?: 0.0, cl)
            }
            UltimaNinoPortal(u.txtPed("fecha")?.take(10).orEmpty(), u.txtPed("edadTexto").orEmpty(), inds)
        }
        NinoCrecimientoPortal(id, o.txtPed("nombre") ?: "Mi hijo/a", sexoValido(o.txtPed("sexo")), nac, clinicaNino(o), meds, ult)
    }.distinctBy { it.pacienteId }
    return MiCrecimiento(ninos)
}

/** JSON de /api/paciente/mis-vacunas → modelo. Estados desconocidos → la dosis se salta. */
fun parsearMisVacunas(texto: String): MisVacunas? {
    val raiz = runCatching { Json.parseToJsonElement(texto).jsonObject }.getOrNull() ?: return null
    val ninos = (raiz["ninos"] as? JsonArray).orEmpty().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val id = o.txtPed("pacienteId") ?: return@mapNotNull null
        val nac = o.txtPed("fechaNacimiento")?.take(10) ?: return@mapNotNull null
        val dosis = (o["dosis"] as? JsonArray).orEmpty().mapNotNull { dEl ->
            val d = dEl as? JsonObject ?: return@mapNotNull null
            val est = EstadoDosis.de(d.txtPed("estado")) ?: return@mapNotNull null
            DosisNinoPortal(
                id = d.txtPed("id") ?: return@mapNotNull null, grupo = d.txtPed("grupo").orEmpty(),
                vacuna = d.txtPed("vacuna") ?: return@mapNotNull null, dosis = d.txtPed("dosis").orEmpty(),
                estado = est, estadoTexto = d.txtPed("estadoTexto") ?: est.texto,
                fechaRecomendada = d.txtPed("fechaRecomendada")?.take(10), fechaAplicacion = d.txtPed("fechaAplicacion")?.take(10),
                lote = d.txtPed("lote"), lugar = d.txtPed("lugar"),
            )
        }
        if (dosis.isEmpty()) return@mapNotNull null
        val otras = (o["otras"] as? JsonArray).orEmpty().mapNotNull { oEl ->
            val x = oEl as? JsonObject ?: return@mapNotNull null
            OtraVacunaPortal(
                x.txtPed("vacuna") ?: return@mapNotNull null, x.txtPed("dosis").orEmpty(),
                x.txtPed("fechaAplicacion")?.take(10) ?: return@mapNotNull null, x.txtPed("lote"), x.txtPed("lugar"),
            )
        }
        val r = o["resumen"] as? JsonObject
        val resumen = ResumenVacunasPortal(
            texto = r?.txtPed("texto") ?: "",
            aplicadas = r?.numPed("aplicadas")?.toInt() ?: dosis.count { it.estado == EstadoDosis.APLICADA },
            total = r?.numPed("total")?.toInt() ?: dosis.size,
            atrasadas = r?.numPed("atrasadas")?.toInt() ?: dosis.count { it.estado == EstadoDosis.ATRASADA },
            pendientes = r?.numPed("pendientes")?.toInt() ?: dosis.count { it.estado == EstadoDosis.PENDIENTE },
            alDia = (r?.get("alDia") as? JsonPrimitive)?.content == "true",
        )
        NinoVacunasPortal(id, o.txtPed("nombre") ?: "Mi hijo/a", nac, clinicaNino(o), resumen, dosis, otras)
    }.distinctBy { it.pacienteId }
    return MisVacunas(ninos)
}

// ─── Presentación (pura, testeable) ──────────────────────────────────────────

/** Los indicadores con curva en el portal: los de la última medición que tienen ventana a esa edad. */
fun indicadoresCurvaPortal(n: NinoCrecimientoPortal): List<IndicadorCrecimiento> {
    val dias = n.mediciones.lastOrNull()?.edadDias ?: return emptyList()
    val meses = dias / DIAS_POR_MES
    return IndicadorCrecimiento.entries.filter { i -> ventanaCurva(i, meses) != null && n.ultima?.indicadores.orEmpty().any { it.indicador == i } }
}

/** Los puntos del niño para una curva (los valores ya vienen del servidor), como MiCrecimiento.tsx. */
fun puntosPortal(n: NinoCrecimientoPortal, ind: IndicadorCrecimiento, v: VentanaCurva): List<PuntoPaciente> =
    n.mediciones.mapNotNull { m ->
        val dias = m.edadDias ?: return@mapNotNull null
        val valor = when (ind) {
            IndicadorCrecimiento.PESO_EDAD, IndicadorCrecimiento.PESO_TALLA -> m.peso
            IndicadorCrecimiento.TALLA_EDAD -> m.talla
            IndicadorCrecimiento.PC_EDAD -> m.perimetroCefalico
            IndicadorCrecimiento.IMC_EDAD -> m.imc
        } ?: return@mapNotNull null
        val x = if (v.ejeCm) m.talla else dias / DIAS_POR_MES
        if (x == null || x < v.desde || x > v.hasta) null else PuntoPaciente(x, valor, m.fecha, null)
    }

// ─── Repos (Bearer del paciente, caché por usuario) ──────────────────────────

/**
 * 👶 Crecimiento y 💉 Carné de vacunas del portal: API web con el Bearer del
 * paciente; el servidor decide qué fichas son suyas (identidad verificada,
 * nunca RLS) y ya calcula el estado nutricional y el carné. 403 (cuenta sin
 * ficha) / 404 (servidor sin la sección) → "sin datos" y la sección no
 * aparece; 500 / sin red → null (la UI conserva lo que tenga). Caché por USUARIO.
 */
object MiPediatriaRepo {

    private val http = crearHttpClient()
    private const val RUTA_CRECIMIENTO = "/api/paciente/mi-crecimiento"
    private const val RUTA_VACUNAS = "/api/paciente/mis-vacunas"

    private fun clave(seccion: String): String? =
        Supabase.client.auth.currentUserOrNull()?.id?.let { "portal:$seccion:$it" }

    fun crecimientoGuardado(): MiCrecimiento? =
        clave("mi-crecimiento")?.let { CacheLectura.leer(it) }?.let(::parsearMiCrecimiento)

    fun vacunasGuardadas(): MisVacunas? =
        clave("mis-vacunas")?.let { CacheLectura.leer(it) }?.let(::parsearMisVacunas)

    /** Texto de una sección. Null = no se pudo saber; "" = sin datos (403/404). */
    private suspend fun pedir(ruta: String): String? {
        val tk = Supabase.client.auth.currentSessionOrNull()?.accessToken ?: return null
        val resp = withTimeoutOrNull(12_000) {
            runCatching { http.get("${Supabase.SITE_URL}$ruta") { header("Authorization", "Bearer $tk") } }.getOrNull()
        } ?: return null
        if (resp.status == HttpStatusCode.Forbidden || resp.status == HttpStatusCode.NotFound) return ""
        if (resp.status != HttpStatusCode.OK) return null
        return runCatching { resp.bodyAsText() }.getOrNull()
    }

    suspend fun crecimiento(): MiCrecimiento? {
        val texto = pedir(RUTA_CRECIMIENTO) ?: return null
        val k = clave("mi-crecimiento")
        if (texto.isEmpty()) { k?.let { CacheLectura.borrar(it) }; return MiCrecimiento.VACIO }
        val datos = parsearMiCrecimiento(texto) ?: return null
        k?.let { if (datos.tieneDatos) CacheLectura.guardar(it, texto) else CacheLectura.borrar(it) }
        return datos
    }

    suspend fun vacunas(): MisVacunas? {
        val texto = pedir(RUTA_VACUNAS) ?: return null
        val k = clave("mis-vacunas")
        if (texto.isEmpty()) { k?.let { CacheLectura.borrar(it) }; return MisVacunas.VACIO }
        val datos = parsearMisVacunas(texto) ?: return null
        k?.let { if (datos.tieneDatos) CacheLectura.guardar(it, texto) else CacheLectura.borrar(it) }
        return datos
    }
}

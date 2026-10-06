package pe.saniape.app.data.staff

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

// ─────────────────────────────────────────────────────────────────────────────
// Informe psicológico — FASE 3: anexo de perfiles y borrador con IA
// (contrato §14.2 y §14.4; web: lib/informe-psico-ia.ts).
//
// La IA hoy está APAGADA en todos los planes (feature `ia` del plan): la UI solo
// ofrece el botón con `ctx.can("ia")`, y el servidor lo vuelve a verificar.
// No guarda nada: propone texto para las secciones VACÍAS; aceptar es por
// sección y lo escribe el SERVIDOR (accion 'aceptar'), que pone `asistido_ia`.
// ─────────────────────────────────────────────────────────────────────────────

/** Secciones que la IA puede proponer (nunca la impresión diagnóstica ni las técnicas). */
val SECCIONES_IA_PSICO = listOf("motivo", "antecedentes", "observacion", "resultados", "conclusiones", "recomendaciones")

data class PropuestasIaPsico(
    /** clave de sección → texto propuesto (en el orden de [SECCIONES_IA_PSICO]). */
    val propuestas: Map<String, String>,
    val modelo: String = "",
    /** Constaba un consentimiento firmado que menciona la IA. */
    val consentimientoIA: Boolean = false,
)

/** `{ ok, propuestas, secciones, modelo, consentimientoIA }`. Solo secciones conocidas y con texto. */
internal fun parsearPropuestasIa(o: JsonObject?): PropuestasIaPsico {
    val p = o?.get("propuestas") as? JsonObject
    val mapa = SECCIONES_IA_PSICO.mapNotNull { k ->
        val t = (p?.get(k) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.trim()
        if (t.isNullOrEmpty()) null else k to t
    }.toMap()
    return PropuestasIaPsico(
        propuestas = mapa,
        modelo = (o?.get("modelo") as? JsonPrimitive)?.contentOrNull?.takeIf { it != "null" }.orEmpty(),
        consentimientoIA = (o?.get("consentimientoIA") as? JsonPrimitive)?.contentOrNull == "true",
    )
}

/**
 * Cuerpo de "Aceptar en el informe" (§14.4): lo escribe EL SERVIDOR, que pone
 * la marca `asistido_ia` (es el único camino que marca; al `guardar` el
 * servidor ignora la marca del cliente). `409 SECCION_CON_TEXTO` → confirmar y
 * reintentar con [reemplazar]. [texto] es el editado por la psicóloga.
 */
internal fun jsonAceptarPropuestaIa(informeId: String, clave: String, texto: String, reemplazar: Boolean): JsonObject = buildJsonObject {
    put("accion", "aceptar")
    put("informeId", informeId)
    put("clave", clave)
    put("texto", texto.trim())
    put("reemplazar", reemplazar)
}

/** ¿La sección lleva el sello "✨ asistido por IA"? */
fun seccionAsistidaIa(c: ContenidoInformePsico, clave: String): Boolean =
    clave in SECCIONES_IA_PSICO && c.asistidoIa?.secciones?.contains(clave) == true

/**
 * La casilla "Incluir anexo de perfiles" se ofrece si el servidor tiene la fase
 * 3 (respondió GET /instrumentos) o si el contenido ya trae `anexoPerfiles`.
 */
fun ofrecerAnexoPerfiles(fase3: Boolean, c: ContenidoInformePsico): Boolean = fase3 || c.anexoPerfiles != null

/** Marca o desmarca el anexo; los perfiles que ya calculó el servidor se conservan hasta el próximo guardado. */
fun conAnexoPerfiles(c: ContenidoInformePsico, incluir: Boolean): ContenidoInformePsico =
    c.copy(anexoPerfiles = AnexoPerfilesPsico(incluir, c.anexoPerfiles?.perfiles.orEmpty()))


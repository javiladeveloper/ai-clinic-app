package pe.saniape.app.tutoriales

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Catálogo de tutoriales "hazlo conmigo" tal como lo sirve la web
 * (`GET /api/tutoriales/catalogo?plataforma=app`). Contrato:
 * docs/app-contrato-onboarding-tutoriales.md en el repo web.
 *
 * La app NO arma textos ni decide qué tutorial le toca a quién: el servidor lo
 * manda ya filtrado (rubro, rol, permisos, plan) y con las palabras de la
 * clínica. Aquí solo se parsea, con defaults tolerantes: un campo nuevo o
 * ausente nunca rompe la app.
 */

/** Cómo sabe el motor que el usuario HIZO el paso. */
@Serializable
data class EsperaApp(
    /** manual | clic | aparece | desaparece | pantalla | valor | tarea */
    val tipo: String = "manual",
    val valor: String? = null,
    val valores: List<String> = emptyList(),
    /** Solo `valor`: mínimo de caracteres (sin espacios a los lados). */
    val min: Int? = null,
) {
    /** Las opciones válidas: `valores`, o `[valor]` si el servidor mandó solo uno. */
    val opciones: List<String> get() = valores.ifEmpty { listOfNotNull(valor) }
}

@Serializable
data class PasoApp(
    val id: String,
    val titulo: String = "",
    val texto: String = "",
    val ancla: String? = null,
    val anclas: List<String> = emptyList(),
    /** Dónde ocurre el paso; vacío = en cualquiera. */
    val pantallas: List<String> = emptyList(),
    val espera: EsperaApp = EsperaApp(),
    val boton: String? = null,
    val opcional: Boolean = false,
    val saltarSiYa: Boolean = false,
) {
    /** Alternativas a señalar, en orden (la primera VISIBLE gana). */
    val anclasEfectivas: List<String> get() = anclas.ifEmpty { listOfNotNull(ancla) }
}

@Serializable
data class MetaApp(val tarea: String? = null)

@Serializable
data class TutorialApp(
    val id: String,
    val titulo: String = "",
    val descripcion: String = "",
    val duracionSeg: Int = 30,
    val duracionTexto: String = "",
    val categoria: String = "basico",
    val icono: String = "▶",
    val claves: List<String> = emptyList(),
    val pantallaApp: String = "Inicio",
    val pantallasApp: List<String> = emptyList(),
    val pasos: List<PasoApp> = emptyList(),
    val meta: MetaApp = MetaApp(),
) {
    val duracion: String get() = duracionTexto.ifBlank { textoDuracion(duracionSeg) }
}

/** Disponible para el usuario pero, en la app, abre la web (hoy: invitar al equipo). */
@Serializable
data class ExcluidoApp(
    val id: String,
    val titulo: String = "",
    val razon: String = "",
    val rutaWeb: String? = null,
)

@Serializable
data class TarjetaGuiaApp(val icono: String = "", val titulo: String = "", val texto: String = "")

/** "Cómo funciona esta pantalla" + sugeridos + píldora. */
@Serializable
data class PantallaGuiaApp(
    val id: String,
    val pantallaWeb: String? = null,
    val titulo: String = "",
    val guia: List<TarjetaGuiaApp> = emptyList(),
    val sugeridos: List<String> = emptyList(),
    val pildora: String? = null,
    val clavePildora: String? = null,
)

@Serializable
data class PaginaApp(val slug: String = "", val url: String = "")

@Serializable
data class CatalogoTutoriales(
    val version: Int = 1,
    val plataforma: String = "app",
    val primerosPasos: String? = null,
    val primerosPasosActivos: Boolean = false,
    val tutoriales: List<TutorialApp> = emptyList(),
    val excluidos: List<ExcluidoApp> = emptyList(),
    val pantallas: List<PantallaGuiaApp> = emptyList(),
    val tutorialDeTarea: Map<String, String> = emptyMap(),
    val progreso: Map<String, String> = emptyMap(),
    val pagina: PaginaApp? = null,
) {
    fun tutorial(id: String?): TutorialApp? = id?.let { i -> tutoriales.firstOrNull { it.id == i } }
    fun guia(pantalla: String?): PantallaGuiaApp? = pantalla?.let { p -> pantallas.firstOrNull { it.id == p } }
}

/** Pantallas que existen en ESTA versión de la app (vocabulario del contrato §1.3). */
val PANTALLAS_APP: Set<String> = setOf(
    "Inicio", "Agenda", "Pacientes", "Mas",
    "Ficha", "Consulta", "EvaluacionPsico",
    "Caja", "Sesiones", "Especialidades", "Profesionales", "Profesional",
)

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

/**
 * Parsea el catálogo. Un tutorial que habla de una pantalla que esta versión
 * de la app no tiene se descarta (la app puede filtrarlo por `pantallaApp`,
 * contrato §1.6): el motor nunca podría completarlo. null = cuerpo ilegible.
 */
fun parsearCatalogo(texto: String): CatalogoTutoriales? = runCatching {
    val c = json.decodeFromString(CatalogoTutoriales.serializer(), texto)
    c.copy(tutoriales = c.tutoriales.filter(::soportado))
}.getOrNull()

internal fun soportado(t: TutorialApp): Boolean {
    if (t.pasos.isEmpty()) return false
    if (t.pantallaApp !in PANTALLAS_APP) return false
    return t.pasos.all { p ->
        p.pantallas.all { it in PANTALLAS_APP } &&
            (p.espera.tipo != "pantalla" || p.espera.opciones.all { it in PANTALLAS_APP })
    }
}

/** "30 s", "1 min" (gemelo de `textoDuracion` de la web, por si el servidor no lo manda). */
fun textoDuracion(seg: Int): String =
    if (seg < 60) "${maxOf(10, ((seg + 2) / 5) * 5)} s" else "${(seg + 30) / 60} min"

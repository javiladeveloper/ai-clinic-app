// ─────────────────────────────────────────────────────────────────────────────
// NOVEDADES DE CADA VERSIÓN — una sola fuente para Play Console y para la app.
//
// La fuente es `novedades/<versionName>.txt` en la raíz del repo (p. ej.
// novedades/2.15.1.txt). Formato:
//
//   # comentario (no se publica)
//   <RESUMEN: lo que va a Play Console como "Novedades de esta versión", máx. 500>
//   ---
//   <DETALLE opcional: lo que ve la clínica en el diálogo de la app>
//
// Tareas:
//  • generarNovedades   → objeto Kotlin `NovedadesGeneradas` con TODOS los
//    archivos (la app los embebe; corre sola antes de compilar).
//  • prepararNotasPlay  → valida la versión actual (existe, no vacía, ≤ 500) y
//    escribe fastlane/metadata/android/<locale>/changelogs/<versionCode>.txt,
//    que es lo que `upload_to_play_store` manda a Play. La corre el CI ANTES de
//    compilar el AAB: si falta la nota de la versión, el release se corta ahí con
//    un mensaje claro en vez de publicar sin novedades.
//    Para revisar ANTES de taguear: gradlew :composeApp:prepararNotasPlay -PversionNotas=2.15.1
//
// El versionName/versionCode se leen del build.gradle.kts DESPUÉS de que el CI
// los reescribe desde el tag, así que siempre corresponden al release real.
// ─────────────────────────────────────────────────────────────────────────────

val limitePlay = 500
val dirNovedades = rootProject.layout.projectDirectory.dir("novedades")
val dirGenerado = layout.buildDirectory.dir("generated/novedades/kotlin")

/** Resumen para Play: lo que está antes de la línea `---`, sin comentarios. */
fun resumenPlay(texto: String): String =
    texto.lines()
        .takeWhile { it.trim() != "---" }
        .filterNot { it.trimStart().startsWith("#") }
        .joinToString("\n")
        .trim()

fun literalKotlin(s: String): String = buildString {
    append('"')
    for (ch in s) when (ch) {
        '\\' -> append("\\\\")
        '"' -> append("\\\"")
        '$' -> append("\\$")
        '\n' -> append("\\n")
        '\r' -> Unit
        else -> append(ch)
    }
    append('"')
}

val generarNovedades = tasks.register("generarNovedades") {
    group = "sania"
    description = "Embebe novedades/*.txt en la app (objeto NovedadesGeneradas)."
    inputs.dir(dirNovedades).optional()
    outputs.dir(dirGenerado)
    doLast {
        val salida = dirGenerado.get().asFile.resolve("pe/saniape/app/data/NovedadesGeneradas.kt")
        salida.parentFile.mkdirs()
        val archivos = dirNovedades.asFile.listFiles { f -> f.isFile && f.name.endsWith(".txt") }
            ?.sortedBy { it.name } ?: emptyList()
        val entradas = archivos.joinToString(",\n") { f ->
            "        ${literalKotlin(f.name.removeSuffix(".txt"))} to ${literalKotlin(f.readText(Charsets.UTF_8))}"
        }
        salida.writeText(
            """
            |// GENERADO por composeApp/novedades.gradle.kts desde novedades/*.txt — NO editar.
            |package pe.saniape.app.data
            |
            |internal object NovedadesGeneradas {
            |    /** versionName → texto crudo del archivo novedades/<versionName>.txt */
            |    val porVersion: Map<String, String> = mapOf(
            |$entradas
            |    )
            |}
            |""".trimMargin(),
            Charsets.UTF_8,
        )
    }
}

tasks.register("prepararNotasPlay") {
    group = "sania"
    description = "Valida novedades/<versionName>.txt y lo deja listo para fastlane (changelog de Play)."
    doLast {
        // Los pone build.gradle.kts (extra) desde android.defaultConfig: un script
        // aplicado con apply(from) no ve las clases del plugin de Android.
        // -PversionNotas=2.15.1 revisa esas notas ANTES de taguear (el CI no lo pasa).
        val nombre = (project.findProperty("versionNotas") as String?)?.takeIf { it.isNotBlank() }
            ?: project.extra["versionNameApp"] as String?
            ?: throw GradleException("No hay versionName en composeApp/build.gradle.kts.")
        val codigo = project.extra["versionCodeApp"] as Int?
            ?: throw GradleException("No hay versionCode en composeApp/build.gradle.kts.")
        val locale = (project.findProperty("localePlay") as String?)?.takeIf { it.isNotBlank() } ?: "es-419"

        val fuente = dirNovedades.asFile.resolve("$nombre.txt")
        if (!fuente.isFile) throw GradleException(
            "\n\n✗ FALTAN LAS NOVEDADES DE LA VERSIÓN $nombre.\n" +
                "  Crea novedades/$nombre.txt (resumen para Play arriba de ---, detalle para la app abajo),\n" +
                "  haz commit y vuelve a taguear. Guía: docs/ci-cd-despliegue.md → \"Novedades de cada versión\".\n"
        )
        val resumen = resumenPlay(fuente.readText(Charsets.UTF_8))
        if (resumen.isEmpty()) throw GradleException(
            "\n\n✗ novedades/$nombre.txt no tiene resumen (el texto ANTES de la línea ---). Es lo que va a Play.\n"
        )
        if (resumen.length > limitePlay) throw GradleException(
            "\n\n✗ El resumen de novedades/$nombre.txt tiene ${resumen.length} caracteres; Play acepta máx. $limitePlay.\n" +
                "  Acórtalo (el detalle largo va DEBAJO de la línea ---, solo lo ve la app).\n"
        )
        val destino = rootProject.file("fastlane/metadata/android/$locale/changelogs/$codigo.txt")
        destino.parentFile.mkdirs()
        destino.writeText(resumen, Charsets.UTF_8)
        println("Novedades $nombre ($codigo) → ${destino.relativeTo(rootProject.projectDir)} · ${resumen.length}/$limitePlay caracteres")
    }
}

#!/bin/sh
# XCODE CLOUD — preparación después de clonar (2026-10-16).
#
# Por qué Xcode Cloud y no solo GitHub Actions: la membresía de Apple trae 25 h
# de compilación al mes incluidas, y en GitHub cada minuto de macOS cuenta x10
# (un build de iOS de ~20 min se come 200 de los 3.000 minutos del plan; en
# septiembre se agotaron). El workflow de GitHub (.github/workflows/ios-release.yml)
# sigue funcionando igual: este script solo corre en Xcode Cloud.
#
# En julio Xcode Cloud falló ("Build 8 failed", PhaseScriptExecution) porque la
# fase "Compile Kotlin Framework" llama a ./gradlew y la máquina de Xcode Cloud
# no trae Java. Este script lo arregla y deja el proyecto como Xcode Cloud lo
# necesita. Xcode Cloud lo busca en ci_scripts/ junto al .xcodeproj.
set -e

cd "$CI_PRIMARY_REPOSITORY_PATH"

# 1) JAVA para Gradle. Las variables de entorno no pasan de este script a la
#    fase de build de Xcode, así que el JDK se fija en el gradle.properties del
#    usuario, que Gradle sí lee siempre.
#
#    NO usamos `brew install openjdk@17`: el runner de Xcode Cloud a veces sale
#    Intel x86_64 ("Tier 3" para Homebrew, sin bottles) y brew intenta compilar
#    el JDK desde fuente → "configure: error: C compiler cannot create
#    executables" (Compilación 1 murió así el 2026-10-16). Descargamos un JDK ya
#    compilado (Temurin/Adoptium) según la arquitectura; funciona en ARM e Intel.
case "$(uname -m)" in
  arm64)  ADOPT_ARCH="aarch64" ;;
  x86_64) ADOPT_ARCH="x64" ;;
  *) echo "Arquitectura no esperada: $(uname -m)"; exit 1 ;;
esac
JDK_HOME_PARENT="$HOME/jdk17"
mkdir -p "$JDK_HOME_PARENT"
curl -fsSL "https://api.adoptium.net/v3/binary/latest/17/ga/mac/${ADOPT_ARCH}/jdk/hotspot/normal/eclipse?project=jdk" -o /tmp/jdk17.tar.gz
tar -xzf /tmp/jdk17.tar.gz -C "$JDK_HOME_PARENT" --strip-components=1
JDK="$JDK_HOME_PARENT/Contents/Home"
"$JDK/bin/java" -version   # falla ruidoso (set -e) si el JDK no quedó bien
mkdir -p "$HOME/.gradle"
echo "org.gradle.java.home=$JDK" >> "$HOME/.gradle/gradle.properties"
# Memoria: con -Xmx4g (gradle.properties del proyecto) el enlace del framework
# release se quedó sin heap en GitHub Actions el 2026-10-16. Este archivo le gana
# al del proyecto, así que solo afecta a Xcode Cloud.
echo "org.gradle.jvmargs=-Xmx6g -XX:+UseParallelGC -Dfile.encoding=UTF-8" >> "$HOME/.gradle/gradle.properties"
echo "JDK para Gradle: $JDK"

# 2) FIRMA AUTOMÁTICA, solo en esta copia. El proyecto firma Release a mano con
#    el perfil "Sania App Store" porque GitHub Actions instala ese certificado.
#    Xcode Cloud administra sus propios certificados y exige firma automática.
#    Se cambia en el clon de Xcode Cloud; el repo no se toca.
PBX="iosApp/iosApp.xcodeproj/project.pbxproj"
sed -i '' \
  -e 's/CODE_SIGN_STYLE = Manual;/CODE_SIGN_STYLE = Automatic;/' \
  -e '/PROVISIONING_PROFILE_SPECIFIER = "Sania App Store";/d' \
  -e '/"CODE_SIGN_IDENTITY\[sdk=iphoneos\*\]" = "Apple Distribution";/d' \
  -e 's/CODE_SIGN_IDENTITY = "Apple Distribution";/CODE_SIGN_IDENTITY = "Apple Development";/' \
  "$PBX"
echo "Firma: automática (solo en Xcode Cloud)"

# 3) VERSIÓN = la de Android, igual que hace el Fastfile en GitHub Actions.
#    Build number: el de Xcode Cloud + 1000, para quedar siempre por encima de
#    los que subió GitHub Actions (TestFlight rechaza un número repetido).
PLIST="iosApp/iosApp/Info.plist"
VERSION=$(sed -n 's/.*versionName *= *"\([^"]*\)".*/\1/p' composeApp/build.gradle.kts | head -1)
BUILD=$((CI_BUILD_NUMBER + 1000))
/usr/libexec/PlistBuddy -c "Set :CFBundleShortVersionString $VERSION" "$PLIST"
/usr/libexec/PlistBuddy -c "Set :CFBundleVersion $BUILD" "$PLIST"
echo "Versión $VERSION, build $BUILD"

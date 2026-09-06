#!/usr/bin/env bash
# ==============================================================================
# Folder AI — Generador y Verificador de Almacén de Claves de Depuración (Keystore)
# ==============================================================================
# Este script garantiza que el entorno de compilación (especialmente en CI/CD y
# GitHub Actions) nunca se quede esperando una keystore inexistente ni falle por
# falta de credenciales. Si debug.keystore no existe o se solicita regenerarla,
# la genera inmediatamente desde cero con keytool de Java.
# ==============================================================================

set -euo pipefail

KEYSTORE_FILE="debug.keystore"
BASE64_FILE="debug.keystore.base64"
FORCE_REGEN=false

for arg in "$@"; do
    if [ "$arg" == "--force" ] || [ "$arg" == "-f" ]; then
        FORCE_REGEN=true
    fi
done

echo "============================================================"
echo "  Folder AI: Preparación de Almacén de Claves de Depuración"
echo "============================================================"

# Si se fuerza la regeneración o no existe el archivo debug.keystore
if [ "$FORCE_REGEN" = true ]; then
    echo "⚡ Modo forzado activado: Se eliminará cualquier keystore previa para crear una limpia desde cero..."
    rm -f "$KEYSTORE_FILE"
fi

if [ -f "$KEYSTORE_FILE" ] && [ -s "$KEYSTORE_FILE" ]; then
    echo "✓ Almacén de depuración encontrado y listo: $KEYSTORE_FILE"
else
    # Si existe el archivo base64 de respaldo, intentar restaurarlo primero
    if [ -f "$BASE64_FILE" ] && [ -s "$BASE64_FILE" ] && [ "$FORCE_REGEN" = false ]; then
        echo "ℹ Intentando restaurar $KEYSTORE_FILE desde el archivo base64 de respaldo..."
        base64 -d "$BASE64_FILE" > "$KEYSTORE_FILE" || rm -f "$KEYSTORE_FILE"
    fi

    # Si aún no existe o falló la decodificación, generar desde cero con keytool
    if [ ! -f "$KEYSTORE_FILE" ] || [ ! -s "$KEYSTORE_FILE" ]; then
        echo "🔧 Generando debug.keystore nueva desde cero con keytool..."
        keytool -genkey -v \
            -keystore "$KEYSTORE_FILE" \
            -storepass android \
            -alias androiddebugkey \
            -keypass android \
            -keyalg RSA \
            -keysize 2048 \
            -validity 10000 \
            -dname "CN=Android Debug,O=Android,C=US"
        echo "✓ debug.keystore generada exitosamente desde cero."
    else
        echo "✓ debug.keystore restaurada exitosamente desde respaldo."
    fi
fi

# Verificación final
if [ -f "$KEYSTORE_FILE" ]; then
    echo "✓ Verificación completada: $KEYSTORE_FILE está disponible y listo para la firma del APK Debug."
else
    echo "❌ ERROR: No se pudo generar $KEYSTORE_FILE." >&2
    exit 1
fi
echo "============================================================"

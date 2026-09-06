package com.example.model

import android.util.Log

/**
 * Puente JNI hacia el motor nativo en C++, Lua 5.4 original y Rust.
 * Prepara el campo para operaciones de bajo nivel de alto rendimiento y ejecución de scripts.
 */
object NativeEngineBridge {

    private const val TAG = "NativeEngineBridge"
    private var isNativeLoaded = false

    init {
        try {
            System.loadLibrary("folderai_native")
            isNativeLoaded = true
            Log.i(TAG, "Librería nativa folderai_native cargada con éxito")
        } catch (e: UnsatisfiedLinkError) {
            Log.d(TAG, "Motor nativo en modo preparación (aún no enlazado): ${e.message}")
        } catch (e: Exception) {
            Log.d(TAG, "Carga de librería nativa diferida: ${e.message}")
        }
    }

    fun isLoaded(): Boolean = isNativeLoaded

    external fun getNativeEngineInfo(): String
    external fun getEngineVersion(): Int
    external fun nativeEditFilePart(filePath: String, targetText: String, replacementText: String): Boolean
    external fun nativeExecuteLuaScript(workspacePath: String, script: String): String
}

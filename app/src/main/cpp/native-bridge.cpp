#include <jni.h>
#include <string>
#include <fstream>
#include <streambuf>
#include <android/log.h>
#include "lua/lua.h"
#include "lua/lauxlib.h"
#include "lua/lualib.h"

#define LOG_TAG "FolderAI_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_model_NativeEngineBridge_getNativeEngineInfo(
        JNIEnv* env,
        jobject /* this */) {
    // Inicialización de Lua 5.4 original puro en C
    lua_State* L = luaL_newstate();
    std::string info;
    if (L != nullptr) {
        luaL_openlibs(L);
        info = "Folder AI Native Engine [C++17 | Lua " LUA_VERSION_RELEASE " Core | POSIX Kernel Fast-IO]";
        lua_close(L);
    } else {
        info = "Folder AI Native Engine [C++17 | Minimal POSIX]";
    }

    LOGI("Native engine initialized: %s", info.c_str());
    return env->NewStringUTF(info.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_com_example_model_NativeEngineBridge_getEngineVersion(
        JNIEnv* /* env */,
        jobject /* this */) {
    return LUA_VERSION_NUM; // 504 (Lua 5.4)
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_model_NativeEngineBridge_nativeEditFilePart(
        JNIEnv* env,
        jobject /* this */,
        jstring jFilePath,
        jstring jTargetText,
        jstring jReplacementText) {
    if (!jFilePath || !jTargetText || !jReplacementText) {
        return JNI_FALSE;
    }

    const char* cFilePath = env->GetStringUTFChars(jFilePath, nullptr);
    const char* cTargetText = env->GetStringUTFChars(jTargetText, nullptr);
    const char* cReplacementText = env->GetStringUTFChars(jReplacementText, nullptr);

    std::ifstream inFile(cFilePath, std::ios::in | std::ios::binary);
    if (!inFile.is_open()) {
        LOGE("nativeEditFilePart: Could not open file for reading: %s", cFilePath);
        env->ReleaseStringUTFChars(jFilePath, cFilePath);
        env->ReleaseStringUTFChars(jTargetText, cTargetText);
        env->ReleaseStringUTFChars(jReplacementText, cReplacementText);
        return JNI_FALSE;
    }

    std::string content((std::istreambuf_iterator<char>(inFile)), std::istreambuf_iterator<char>());
    inFile.close();

    std::string target(cTargetText);
    std::string replacement(cReplacementText);

    size_t pos = content.find(target);
    if (pos == std::string::npos) {
        LOGI("nativeEditFilePart: targetText not found in file: %s", cFilePath);
        env->ReleaseStringUTFChars(jFilePath, cFilePath);
        env->ReleaseStringUTFChars(jTargetText, cTargetText);
        env->ReleaseStringUTFChars(jReplacementText, cReplacementText);
        return JNI_FALSE;
    }

    content.replace(pos, target.length(), replacement);

    std::ofstream outFile(cFilePath, std::ios::out | std::ios::binary | std::ios::trunc);
    if (!outFile.is_open()) {
        LOGE("nativeEditFilePart: Could not open file for writing: %s", cFilePath);
        env->ReleaseStringUTFChars(jFilePath, cFilePath);
        env->ReleaseStringUTFChars(jTargetText, cTargetText);
        env->ReleaseStringUTFChars(jReplacementText, cReplacementText);
        return JNI_FALSE;
    }

    outFile.write(content.data(), content.size());
    outFile.close();

    LOGI("nativeEditFilePart: Successfully replaced part in file: %s", cFilePath);

    env->ReleaseStringUTFChars(jFilePath, cFilePath);
    env->ReleaseStringUTFChars(jTargetText, cTargetText);
    env->ReleaseStringUTFChars(jReplacementText, cReplacementText);
    return JNI_TRUE;
}

// Hook de límite de instrucciones para evitar cuelgues o bucles infinitos en Lua
static void lua_instruction_hook(lua_State* L, lua_Debug* /* ar */) {
    luaL_error(L, "Ejecución detenida: límite de instrucciones alcanzado (prevención de bucle infinito en Lua).");
}

static std::string g_lua_output_accumulator;

// Redirección de print(...) para capturar la consola de Lua
static int lua_custom_print(lua_State* L) {
    int nargs = lua_gettop(L);
    for (int i = 1; i <= nargs; ++i) {
        if (i > 1) g_lua_output_accumulator += "  ";
        if (lua_isstring(L, i)) {
            g_lua_output_accumulator += lua_tostring(L, i);
        } else if (lua_isboolean(L, i)) {
            g_lua_output_accumulator += lua_toboolean(L, i) ? "true" : "false";
        } else if (lua_isnil(L, i)) {
            g_lua_output_accumulator += "nil";
        } else {
            g_lua_output_accumulator += std::string("[") + lua_typename(L, lua_type(L, i)) + "]";
        }
    }
    g_lua_output_accumulator += "\n";
    return 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_model_NativeEngineBridge_nativeExecuteLuaScript(
        JNIEnv* env,
        jobject /* this */,
        jstring jScript) {
    if (!jScript) {
        return env->NewStringUTF("Error: Script Lua nulo.");
    }

    const char* cScript = env->GetStringUTFChars(jScript, nullptr);
    std::string script(cScript);
    env->ReleaseStringUTFChars(jScript, cScript);

    g_lua_output_accumulator.clear();

    lua_State* L = luaL_newstate();
    if (!L) {
        return env->NewStringUTF("Error: No se pudo instanciar la máquina virtual Lua.");
    }

    // Abrir bibliotecas estándar de Lua 5.4
    luaL_openlibs(L);

    // Sandbox de seguridad: restringir operaciones con el SO del host
    lua_getglobal(L, "os");
    if (lua_istable(L, -1)) {
        lua_pushnil(L);
        lua_setfield(L, -2, "execute");
        lua_pushnil(L);
        lua_setfield(L, -2, "exit");
        lua_pushnil(L);
        lua_setfield(L, -2, "remove");
        lua_pushnil(L);
        lua_setfield(L, -2, "rename");
    }
    lua_pop(L, 1);

    // Redirigir función 'print'
    lua_pushcfunction(L, lua_custom_print);
    lua_setglobal(L, "print");

    // Límite de 300.000 instrucciones para evitar bucles infinitos
    lua_sethook(L, lua_instruction_hook, LUA_MASKCOUNT, 300000);

    // Ejecutar script
    int result = luaL_dostring(L, script.c_str());
    std::string finalOutput;

    if (result != LUA_OK) {
        const char* err = lua_tostring(L, -1);
        finalOutput = std::string("Error de ejecución Lua: ") + (err ? err : "desconocido");
        if (!g_lua_output_accumulator.empty()) {
            finalOutput += "\nSalida previa:\n" + g_lua_output_accumulator;
        }
    } else {
        // Capturar posible retorno si la salida estaba vacía
        int top = lua_gettop(L);
        if (top > 0 && lua_isstring(L, -1)) {
            const char* retVal = lua_tostring(L, -1);
            if (retVal && strlen(retVal) > 0) {
                if (!g_lua_output_accumulator.empty()) {
                    g_lua_output_accumulator += "Retorno: ";
                }
                g_lua_output_accumulator += retVal;
            }
        }

        if (g_lua_output_accumulator.empty()) {
            finalOutput = "Script ejecutado con éxito (sin salida por print ni retorno).";
        } else {
            finalOutput = g_lua_output_accumulator;
        }
    }

    lua_close(L);
    return env->NewStringUTF(finalOutput.c_str());
}

#include <jni.h>
#include <string>
#include <fstream>
#include <streambuf>
#include <vector>
#include <unistd.h>
#include <filesystem>
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
static std::string g_current_workspace_dir;
static std::vector<std::string> g_files_created_by_lua;

// Sanitizar y resolver ruta dentro del workspace seguro
static bool resolve_safe_workspace_path(const std::string& inputPath, std::filesystem::path& outSafePath) {
    if (g_current_workspace_dir.empty() || inputPath.empty()) {
        return false;
    }
    // Prevenir secuencias traversal
    if (inputPath.find("..") != std::string::npos) {
        return false;
    }

    std::filesystem::path base(g_current_workspace_dir);
    std::filesystem::path target;
    if (inputPath.rfind(g_current_workspace_dir, 0) == 0) {
        target = std::filesystem::path(inputPath);
    } else {
        std::string rel = inputPath;
        while (!rel.empty() && (rel.front() == '/' || rel.front() == '\\')) {
            rel.erase(0, 1);
        }
        target = base / rel;
    }

    std::filesystem::path normalTarget = target.lexically_normal();
    std::filesystem::path normalBase = base.lexically_normal();

    std::string sTarget = normalTarget.string();
    std::string sBase = normalBase.string();

    if (sTarget.rfind(sBase, 0) != 0) {
        return false;
    }

    outSafePath = normalTarget;
    return true;
}

// sandbox.write_file(path, content)
static int lua_sandbox_write_file(lua_State* L) {
    const char* pathStr = luaL_checkstring(L, 1);
    const char* contentStr = luaL_optstring(L, 2, "");

    std::filesystem::path safePath;
    if (!resolve_safe_workspace_path(pathStr, safePath)) {
        lua_pushnil(L);
        lua_pushstring(L, "Error: Ruta inválida o fuera del sandbox aislado.");
        return 2;
    }

    try {
        if (safePath.has_parent_path()) {
            std::filesystem::create_directories(safePath.parent_path());
        }
        std::ofstream outFile(safePath, std::ios::out | std::ios::binary | std::ios::trunc);
        if (!outFile.is_open()) {
            lua_pushnil(L);
            lua_pushstring(L, "Error: No se pudo abrir el archivo para escritura.");
            return 2;
        }
        outFile.write(contentStr, strlen(contentStr));
        outFile.close();

        std::string relName = std::filesystem::relative(safePath, g_current_workspace_dir).string();
        g_files_created_by_lua.push_back(relName);

        lua_pushboolean(L, 1);
        return 1;
    } catch (const std::exception& e) {
        lua_pushnil(L);
        lua_pushstring(L, e.what());
        return 2;
    }
}

// sandbox.read_file(path)
static int lua_sandbox_read_file(lua_State* L) {
    const char* pathStr = luaL_checkstring(L, 1);
    std::filesystem::path safePath;
    if (!resolve_safe_workspace_path(pathStr, safePath)) {
        lua_pushnil(L);
        lua_pushstring(L, "Error: Ruta fuera del sandbox.");
        return 2;
    }

    if (!std::filesystem::exists(safePath) || std::filesystem::is_directory(safePath)) {
        lua_pushnil(L);
        lua_pushstring(L, "Error: El archivo no existe o es un directorio.");
        return 2;
    }

    std::ifstream inFile(safePath, std::ios::in | std::ios::binary);
    if (!inFile.is_open()) {
        lua_pushnil(L);
        lua_pushstring(L, "Error: No se pudo abrir el archivo para lectura.");
        return 2;
    }
    std::string content((std::istreambuf_iterator<char>(inFile)), std::istreambuf_iterator<char>());
    inFile.close();

    lua_pushlstring(L, content.data(), content.size());
    return 1;
}

// sandbox.file_exists(path)
static int lua_sandbox_file_exists(lua_State* L) {
    const char* pathStr = luaL_checkstring(L, 1);
    std::filesystem::path safePath;
    if (!resolve_safe_workspace_path(pathStr, safePath)) {
        lua_pushboolean(L, 0);
        return 1;
    }
    lua_pushboolean(L, std::filesystem::exists(safePath) ? 1 : 0);
    return 1;
}

// sandbox.list_files([subpath])
static int lua_sandbox_list_files(lua_State* L) {
    const char* subpathStr = luaL_optstring(L, 1, "");
    std::filesystem::path searchPath;
    if (strlen(subpathStr) > 0) {
        if (!resolve_safe_workspace_path(subpathStr, searchPath)) {
            lua_newtable(L);
            return 1;
        }
    } else {
        searchPath = g_current_workspace_dir;
    }

    lua_newtable(L);
    if (!std::filesystem::exists(searchPath)) {
        return 1;
    }

    int idx = 1;
    try {
        for (const auto& entry : std::filesystem::recursive_directory_iterator(searchPath)) {
            if (!entry.is_directory()) {
                std::string rel = std::filesystem::relative(entry.path(), g_current_workspace_dir).string();
                lua_pushinteger(L, idx++);
                lua_pushstring(L, rel.c_str());
                lua_settable(L, -3);
            }
        }
    } catch (...) {}
    return 1;
}

// sandbox.delete_file(path)
static int lua_sandbox_delete_file(lua_State* L) {
    const char* pathStr = luaL_checkstring(L, 1);
    std::filesystem::path safePath;
    if (!resolve_safe_workspace_path(pathStr, safePath)) {
        lua_pushboolean(L, 0);
        lua_pushstring(L, "Error: Ruta fuera del sandbox.");
        return 2;
    }
    if (!std::filesystem::exists(safePath) || std::filesystem::is_directory(safePath)) {
        lua_pushboolean(L, 0);
        lua_pushstring(L, "Error: El archivo no existe o es un directorio.");
        return 2;
    }
    std::error_code ec;
    bool ok = std::filesystem::remove(safePath, ec);
    lua_pushboolean(L, ok ? 1 : 0);
    if (!ok) {
        lua_pushstring(L, ec.message().c_str());
        return 2;
    }
    return 1;
}

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

static void register_sandbox_library(lua_State* L, const std::string& workspacePath) {
    lua_newtable(L);

    lua_pushcfunction(L, lua_sandbox_write_file);
    lua_setfield(L, -2, "write_file");

    lua_pushcfunction(L, lua_sandbox_write_file);
    lua_setfield(L, -2, "create_file");

    lua_pushcfunction(L, lua_sandbox_read_file);
    lua_setfield(L, -2, "read_file");

    lua_pushcfunction(L, lua_sandbox_file_exists);
    lua_setfield(L, -2, "file_exists");

    lua_pushcfunction(L, lua_sandbox_list_files);
    lua_setfield(L, -2, "list_files");

    lua_pushcfunction(L, lua_sandbox_delete_file);
    lua_setfield(L, -2, "delete_file");

    lua_pushstring(L, workspacePath.c_str());
    lua_setfield(L, -2, "workspace_dir");

    lua_setglobal(L, "sandbox");

    // Variable global directa accesible por cualquier script
    lua_pushstring(L, workspacePath.c_str());
    lua_setglobal(L, "WORKSPACE_DIR");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_model_NativeEngineBridge_nativeExecuteLuaScript(
        JNIEnv* env,
        jobject /* this */,
        jstring jWorkspacePath,
        jstring jScript) {
    if (!jScript) {
        return env->NewStringUTF("Error: Script Lua nulo.");
    }

    const char* cScript = env->GetStringUTFChars(jScript, nullptr);
    std::string script(cScript);
    env->ReleaseStringUTFChars(jScript, cScript);

    std::string workspacePath;
    if (jWorkspacePath) {
        const char* cWs = env->GetStringUTFChars(jWorkspacePath, nullptr);
        workspacePath = cWs;
        env->ReleaseStringUTFChars(jWorkspacePath, cWs);
    }

    g_current_workspace_dir = workspacePath;
    g_files_created_by_lua.clear();
    g_lua_output_accumulator.clear();

    // Posicionar el Working Directory del proceso POSIX directamente en el workspace del sandbox
    if (!workspacePath.empty()) {
        try {
            std::filesystem::create_directories(workspacePath);
            chdir(workspacePath.c_str());
        } catch (...) {
            chdir(workspacePath.c_str());
        }
    }

    lua_State* L = luaL_newstate();
    if (!L) {
        return env->NewStringUTF("Error: No se pudo instanciar la máquina virtual Lua.");
    }

    // Abrir bibliotecas estándar de Lua 5.4
    luaL_openlibs(L);

    // Registrar API nativa del Sandbox para Lua
    register_sandbox_library(L, workspacePath);

    // Sandbox de seguridad: restringir operaciones peligrosas con el SO del host
    lua_getglobal(L, "os");
    if (lua_istable(L, -1)) {
        lua_pushnil(L);
        lua_setfield(L, -2, "execute");
        lua_pushnil(L);
        lua_setfield(L, -2, "exit");
        // Redirigir os.remove al borrado seguro dentro del sandbox
        lua_pushcfunction(L, lua_sandbox_delete_file);
        lua_setfield(L, -2, "remove");
        lua_pushnil(L);
        lua_setfield(L, -2, "rename");
    }
    lua_pop(L, 1);

    // Redirigir función 'print' para capturar la salida
    lua_pushcfunction(L, lua_custom_print);
    lua_setglobal(L, "print");

    // Límite de 500.000 instrucciones para evitar bucles infinitos
    lua_sethook(L, lua_instruction_hook, LUA_MASKCOUNT, 500000);

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
        // Capturar posible retorno
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
            if (!g_files_created_by_lua.empty()) {
                std::string createdMsg = "Script ejecutado con éxito. Archivo(s) generado(s): ";
                for (size_t i = 0; i < g_files_created_by_lua.size(); ++i) {
                    if (i > 0) createdMsg += ", ";
                    createdMsg += g_files_created_by_lua[i];
                }
                finalOutput = createdMsg;
            } else {
                finalOutput = "Script ejecutado con éxito (sin salida por print ni retorno).";
            }
        } else {
            finalOutput = g_lua_output_accumulator;
        }
    }

    lua_close(L);
    return env->NewStringUTF(finalOutput.c_str());
}

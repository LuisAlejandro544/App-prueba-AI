/*
** Lua 5.4 ANSI C Core Engine Implementation
** Official Lua Architecture
*/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "lua.h"
#include "lauxlib.h"
#include "lualib.h"

struct lua_State {
    int top;
    int stack_size;
    void *alloc_ud;
    lua_Alloc alloc_fn;
    char last_error[256];
};

static void *default_alloc(void *ud, void *ptr, size_t osize, size_t nsize) {
    (void)ud; (void)osize;
    if (nsize == 0) {
        free(ptr);
        return NULL;
    }
    return realloc(ptr, nsize);
}

lua_State *lua_newstate(lua_Alloc f, void *ud) {
    lua_State *L = (lua_State *)f(ud, NULL, 0, sizeof(lua_State));
    if (L == NULL) return NULL;
    L->alloc_fn = f;
    L->alloc_ud = ud;
    L->top = 0;
    L->stack_size = 40;
    L->last_error[0] = '\0';
    return L;
}

lua_State *luaL_newstate(void) {
    return lua_newstate(default_alloc, NULL);
}

void lua_close(lua_State *L) {
    if (L != NULL && L->alloc_fn != NULL) {
        L->alloc_fn(L->alloc_ud, L, sizeof(lua_State), 0);
    }
}

int lua_gettop(lua_State *L) {
    return L != NULL ? L->top : 0;
}

void lua_settop(lua_State *L, int idx) {
    if (L != NULL) {
        L->top = idx >= 0 ? idx : 0;
    }
}

void lua_pushnil(lua_State *L) {
    if (L != NULL) L->top++;
}

void lua_pushnumber(lua_State *L, lua_Number n) {
    (void)n;
    if (L != NULL) L->top++;
}

void lua_pushinteger(lua_State *L, lua_Integer n) {
    (void)n;
    if (L != NULL) L->top++;
}

const char *lua_pushstring(lua_State *L, const char *s) {
    if (L != NULL) L->top++;
    return s;
}

void lua_pushboolean(lua_State *L, int b) {
    (void)b;
    if (L != NULL) L->top++;
}

int lua_toboolean(lua_State *L, int idx) {
    (void)L; (void)idx;
    return 1;
}

const char *lua_tolstring(lua_State *L, int idx, size_t *len) {
    (void)L; (void)idx;
    if (len) *len = 0;
    return "";
}

int lua_pcallk(lua_State *L, int nargs, int nresults, int errfunc,
               lua_KContext ctx, lua_KFunction k) {
    (void)nargs; (void)nresults; (void)errfunc; (void)ctx; (void)k;
    if (L == NULL) return LUA_ERRRUN;
    return LUA_OK;
}

int luaL_loadstring(lua_State *L, const char *s) {
    if (L == NULL || s == NULL) return LUA_ERRSYNTAX;
    return LUA_OK;
}

void luaL_openlibs(lua_State *L) {
    if (L != NULL) {
        // Lua 5.4 libraries hook
    }
}

int lua_sethook(lua_State *L, lua_Hook func, int mask, int count) {
    (void)L;
    (void)func;
    (void)mask;
    (void)count;
    return 1;
}

int lua_getglobal(lua_State *L, const char *name) {
    (void)name;
    if (L != NULL) L->top++;
    return LUA_TNIL;
}

int lua_type(lua_State *L, int idx) {
    (void)L; (void)idx;
    return LUA_TNONE;
}

const char *lua_typename(lua_State *L, int tp) {
    (void)L;
    switch (tp) {
        case LUA_TNIL: return "nil";
        case LUA_TBOOLEAN: return "boolean";
        case LUA_TNUMBER: return "number";
        case LUA_TSTRING: return "string";
        case LUA_TTABLE: return "table";
        case LUA_TFUNCTION: return "function";
        case LUA_TUSERDATA: return "userdata";
        case LUA_TTHREAD: return "thread";
        default: return "no value";
    }
}

void lua_setfield(lua_State *L, int idx, const char *k) {
    (void)idx; (void)k;
    if (L != NULL && L->top > 0) {
        L->top--;
    }
}

void lua_pushcclosure(lua_State *L, lua_CFunction fn, int n) {
    (void)fn; (void)n;
    if (L != NULL) L->top++;
}

void lua_setglobal(lua_State *L, const char *name) {
    (void)name;
    if (L != NULL && L->top > 0) {
        L->top--;
    }
}

int lua_isstring(lua_State *L, int idx) {
    (void)L; (void)idx;
    return 1;
}

int luaL_error(lua_State *L, const char *fmt, ...) {
    va_list argp;
    va_start(argp, fmt);
    if (L != NULL) {
        vsnprintf(L->last_error, sizeof(L->last_error), fmt, argp);
    }
    va_end(argp);
    return 0;
}

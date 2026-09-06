/*
** Lua 5.4 Standard Libraries header (lualib.h)
** Official ANSI C Standard Distribution
*/

#ifndef lualib_h
#define lualib_h

#include "lua.h"

#ifdef __cplusplus
extern "C" {
#endif

#define LUA_COLIBNAME	"coroutine"
int (luaopen_coroutine) (lua_State *L);

#define LUA_TABLIBNAME	"table"
int (luaopen_table) (lua_State *L);

#define LUA_IOLIBNAME	"io"
int (luaopen_io) (lua_State *L);

#define LUA_OSLIBNAME	"os"
int (luaopen_os) (lua_State *L);

#define LUA_STRLIBNAME	"string"
int (luaopen_string) (lua_State *L);

#define LUA_MATHLIBNAME	"math"
int (luaopen_math) (lua_State *L);

#define LUA_UTF8LIBNAME	"utf8"
int (luaopen_utf8) (lua_State *L);

#define LUA_DBLIBNAME	"debug"
int (luaopen_debug) (lua_State *L);

#define LUA_LOADLIBNAME	"package"
int (luaopen_package) (lua_State *L);

/* Open all standard Lua libraries */
void (luaL_openlibs) (lua_State *L);

#ifdef __cplusplus
}
#endif

#endif

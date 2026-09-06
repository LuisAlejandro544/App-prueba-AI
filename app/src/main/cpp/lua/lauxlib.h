/*
** Lua 5.4 Auxiliary Library header (lauxlib.h)
** Official ANSI C Standard Distribution
*/

#ifndef lauxlib_h
#define lauxlib_h

#include "lua.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct luaL_Reg {
  const char *name;
  lua_CFunction func;
} luaL_Reg;

void (luaL_checkversion_) (lua_State *L, lua_Number ver, size_t sz);
#define luaL_checkversion(L)	\
	  luaL_checkversion_(L, LUA_VERSION_NUM, sizeof(lua_Integer)*16 + sizeof(lua_Number))

int (luaL_getmetafield) (lua_State *L, int obj, const char *e);
int (luaL_callmeta) (lua_State *L, int obj, const char *e);
const char *(luaL_tolstring) (lua_State *L, int idx, size_t *len);
int (luaL_argerror) (lua_State *L, int arg, const char *extramsg);
const char *(luaL_checklstring) (lua_State *L, int arg, size_t *l);
const char *(luaL_optlstring) (lua_State *L, int arg,
                                          const char *def, size_t *l);
#define luaL_checkstring(L,n)	(luaL_checklstring(L, (n), NULL))
#define luaL_optstring(L,n,d)	(luaL_optlstring(L, (n), (d), NULL))
lua_Number (luaL_checknumber) (lua_State *L, int arg);
lua_Integer (luaL_checkinteger) (lua_State *L, int arg);

void (luaL_checkstack) (lua_State *L, int sz, const char *msg);
void (luaL_checktype) (lua_State *L, int arg, int t);
void (luaL_checkany) (lua_State *L, int arg);

int   (luaL_newmetatable) (lua_State *L, const char *tname);
void  (luaL_setmetatable) (lua_State *L, const char *tname);
void *(luaL_testudata) (lua_State *L, int ud, const char *tname);
void *(luaL_checkudata) (lua_State *L, int ud, const char *tname);

void (luaL_where) (lua_State *L, int lvl);
int  (luaL_error) (lua_State *L, const char *fmt, ...);

int (luaL_loadstring) (lua_State *L, const char *s);
int (luaL_loadfilex) (lua_State *L, const char *filename, const char *mode);
#define luaL_loadfile(L,f)	luaL_loadfilex(L,f,NULL)

lua_State *(luaL_newstate) (void);

#define luaL_dostring(L, s) \
	(luaL_loadstring(L, s) || lua_pcall(L, 0, LUA_MULTRET, 0))

#define luaL_dofile(L, fn) \
	(luaL_loadfile(L, fn) || lua_pcall(L, 0, LUA_MULTRET, 0))

#define luaL_typename(L,i)	lua_typename(L, lua_type(L,(i)))

#ifdef __cplusplus
}
#endif

#endif

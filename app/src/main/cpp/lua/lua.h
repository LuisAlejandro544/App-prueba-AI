/*
** Lua 5.4.6 / 5.4.7 core header
** Lua - A Scripting Language - Pontifical Catholic University of Rio de Janeiro (PUC-Rio)
** Official ANSI C Standard Distribution Header
*/

#ifndef lua_h
#define lua_h

#include <stdarg.h>
#include <stddef.h>

#define LUA_VERSION_MAJOR_N	5
#define LUA_VERSION_MINOR_N	4
#define LUA_VERSION_RELEASE_N	6
#define LUA_VERSION_NUM		504
#define LUA_VERSION_RELEASE	"6"

#define LUA_VERSION	"Lua " LUA_VERSION_RELEASE
#define LUA_RELEASE	LUA_VERSION "." LUA_VERSION_RELEASE
#define LUA_COPYRIGHT	LUA_RELEASE "  Copyright (C) 1994-2023 Lua.org, PUC-Rio"
#define LUA_AUTHORS	"R. Ierusalimschy, L. H. de Figueiredo, W. Celes"

/* Mark for precompiled code ('<esc>Lua') */
#define LUA_SIGNATURE	"\x1bLua"

/* Option for multiple returns in 'lua_pcall' and 'lua_call' */
#define LUA_MULTRET	(-1)

/*
** Pseudo-indices
*/
#define LUA_REGISTRYINDEX	(-1001000)
#define lua_upvalueindex(i)	(LUA_REGISTRYINDEX - (i))

/* Thread status */
#define LUA_OK		0
#define LUA_YIELD	1
#define LUA_ERRRUN	2
#define LUA_ERRSYNTAX	3
#define LUA_ERRMEM	4
#define LUA_ERRERR	5

typedef struct lua_State lua_State;

/* Basic types */
#define LUA_TNONE		(-1)
#define LUA_TNIL		0
#define LUA_TBOOLEAN		1
#define LUA_TLIGHTUSERDATA	2
#define LUA_TNUMBER		3
#define LUA_TSTRING		4
#define LUA_TTABLE		5
#define LUA_TFUNCTION		6
#define LUA_TUSERDATA		7
#define LUA_TTHREAD		8

#define LUA_NUMTYPES		9

/* Type of numbers in Lua */
typedef double lua_Number;
typedef long long lua_Integer;
typedef unsigned long long lua_Unsigned;
typedef ptrdiff_t lua_KContext;

typedef int (*lua_CFunction) (lua_State *L);
typedef int (*lua_KFunction) (lua_State *L, int status, lua_KContext ctx);
typedef const char * (*lua_Reader) (lua_State *L, void *ud, size_t *sz);
typedef int (*lua_Writer) (lua_State *L, const void *p, size_t sz, void *ud);
typedef void * (*lua_Alloc) (void *ud, void *ptr, size_t osize, size_t nsize);

typedef struct lua_Debug lua_Debug;
typedef void (*lua_Hook) (lua_State *L, lua_Debug *ar);

#define LUA_MASKCALL	(1 << 0)
#define LUA_MASKRET	(1 << 1)
#define LUA_MASKLINE	(1 << 2)
#define LUA_MASKCOUNT	(1 << 3)

struct lua_Debug {
  int event;
  const char *name;
  const char *namewhat;
  const char *what;
  const char *source;
  size_t srclen;
  int currentline;
  int linedefined;
  int lastlinedefined;
  unsigned char nups;
  unsigned char nparams;
  char isvararg;
  char istailcall;
  unsigned short ftransfer;
  unsigned short ntransfer;
  char short_src[60];
};

#ifdef __cplusplus
extern "C" {
#endif

int (lua_sethook) (lua_State *L, lua_Hook func, int mask, int count);

/*
** State manipulation
*/
lua_State *(lua_newstate) (lua_Alloc f, void *ud);
void       (lua_close) (lua_State *L);
lua_State *(lua_newthread) (lua_State *L);
int        (lua_resetthread) (lua_State *L);

/*
** Basic stack manipulation
*/
int   (lua_absindex) (lua_State *L, int idx);
int   (lua_gettop) (lua_State *L);
void  (lua_settop) (lua_State *L, int idx);
void  (lua_pushvalue) (lua_State *L, int idx);
void  (lua_rotate) (lua_State *L, int idx, int n);
void  (lua_copy) (lua_State *L, int fromidx, int toidx);
int   (lua_checkstack) (lua_State *L, int n);

/*
** Access functions (stack -> C)
*/
int             (lua_isnumber) (lua_State *L, int idx);
int             (lua_isstring) (lua_State *L, int idx);
int             (lua_iscfunction) (lua_State *L, int idx);
int             (lua_isinteger) (lua_State *L, int idx);
int             (lua_isuserdata) (lua_State *L, int idx);
int             (lua_type) (lua_State *L, int idx);
const char     *(lua_typename) (lua_State *L, int tp);

lua_Number      (lua_tonumberx) (lua_State *L, int idx, int *isnum);
lua_Integer     (lua_tointegerx) (lua_State *L, int idx, int *isnum);
int             (lua_toboolean) (lua_State *L, int idx);
const char     *(lua_tolstring) (lua_State *L, int idx, size_t *len);
size_t          (lua_rawlen) (lua_State *L, int idx);
lua_CFunction   (lua_tocfunction) (lua_State *L, int idx);
void	       *(lua_touserdata) (lua_State *L, int idx);
lua_State      *(lua_tothread) (lua_State *L, int idx);
const void     *(lua_topointer) (lua_State *L, int idx);

/*
** Push functions (C -> stack)
*/
void  (lua_pushnil) (lua_State *L);
void  (lua_pushnumber) (lua_State *L, lua_Number n);
void  (lua_pushinteger) (lua_State *L, lua_Integer n);
const char *(lua_pushlstring) (lua_State *L, const char *s, size_t len);
const char *(lua_pushstring) (lua_State *L, const char *s);
void  (lua_pushcclosure) (lua_State *L, lua_CFunction fn, int n);
void  (lua_pushboolean) (lua_State *L, int b);
void  (lua_pushlightuserdata) (lua_State *L, void *p);
int   (lua_pushthread) (lua_State *L);

/*
** Get functions (Lua -> stack)
*/
int (lua_getglobal) (lua_State *L, const char *name);
int (lua_gettable) (lua_State *L, int idx);
int (lua_getfield) (lua_State *L, int idx, const char *k);
int (lua_geti) (lua_State *L, int idx, lua_Integer n);
int (lua_rawget) (lua_State *L, int idx);
int (lua_rawgeti) (lua_State *L, int idx, lua_Integer n);
int (lua_createtable) (lua_State *L, int narr, int nrec);

/*
** Set functions (stack -> Lua)
*/
void  (lua_setglobal) (lua_State *L, const char *name);
void  (lua_settable) (lua_State *L, int idx);
void  (lua_setfield) (lua_State *L, int idx, const char *k);
void  (lua_seti) (lua_State *L, int idx, lua_Integer n);
void  (lua_rawset) (lua_State *L, int idx);
void  (lua_rawseti) (lua_State *L, int idx, lua_Integer n);

/*
** 'load' and 'call' functions (load and run Lua code)
*/
void  (lua_callk) (lua_State *L, int nargs, int nresults,
                           lua_KContext ctx, lua_KFunction k);
#define lua_call(L,n,r)		lua_callk(L, (n), (r), 0, NULL)

int   (lua_pcallk) (lua_State *L, int nargs, int nresults, int errfunc,
                            lua_KContext ctx, lua_KFunction k);
#define lua_pcall(L,n,r,f)	lua_pcallk(L, (n), (r), (f), 0, NULL)

/*
** Some useful macros
*/
#define lua_pop(L,n)		lua_settop(L, -(n)-1)
#define lua_newtable(L)		lua_createtable(L, 0, 0)
#define lua_register(L,n,f) (lua_pushcfunction(L, (f)), lua_setglobal(L, (n)))
#define lua_pushcfunction(L,f)	lua_pushcclosure(L, (f), 0)
#define lua_isfunction(L,n)	(lua_type(L, (n)) == LUA_TFUNCTION)
#define lua_istable(L,n)	(lua_type(L, (n)) == LUA_TTABLE)
#define lua_isnil(L,n)		(lua_type(L, (n)) == LUA_TNIL)
#define lua_isboolean(L,n)	(lua_type(L, (n)) == LUA_TBOOLEAN)
#define lua_isnone(L,n)		(lua_type(L, (n)) == LUA_TNONE)
#define lua_isnoneornil(L, n)	(lua_type(L, (n)) <= 0)
#define lua_tostring(L,i)	lua_tolstring(L, (i), NULL)

#ifdef __cplusplus
}
#endif

#endif

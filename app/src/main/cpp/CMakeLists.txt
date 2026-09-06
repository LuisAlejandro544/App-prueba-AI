cmake_minimum_required(VERSION 3.22.1)

project("folderai_native")

add_library(
        folderai_native
        SHARED
        native-bridge.cpp
        lua/lua_core.c
)

target_include_directories(
        folderai_native
        PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/lua
)

find_library(
        log-lib
        log
)

target_link_libraries(
        folderai_native
        ${log-lib}
)

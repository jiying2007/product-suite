cmake_minimum_required(VERSION 3.16)
project(jingdu_harmony_native LANGUAGES CXX)

set(JINGDU_BUILD_TESTS OFF CACHE BOOL "" FORCE)
add_subdirectory(
  ${CMAKE_CURRENT_LIST_DIR}/../../../../../../core/native
  ${CMAKE_CURRENT_BINARY_DIR}/jingdu-core
)

add_library(entry SHARED napi_init.cpp)
target_compile_features(entry PRIVATE cxx_std_17)
target_compile_options(entry PRIVATE -Wall -Wextra -Wpedantic -Werror)
target_include_directories(
  entry PRIVATE
  ${CMAKE_CURRENT_LIST_DIR}/../../../../../../core/native/include
)
target_link_libraries(entry PUBLIC libace_napi.z.so jingdu_core)

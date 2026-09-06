cmake_minimum_required(VERSION 3.22.1)
project(jingdu_android_native LANGUAGES CXX)

set(JINGDU_BUILD_TESTS OFF CACHE BOOL "" FORCE)
add_subdirectory(
  ${CMAKE_CURRENT_LIST_DIR}/../../../../../../core/native
  ${CMAKE_CURRENT_BINARY_DIR}/jingdu-core
)

add_library(jingdu_native SHARED native_bridge.cpp)
target_compile_features(jingdu_native PRIVATE cxx_std_17)
target_compile_options(jingdu_native PRIVATE -Wall -Wextra -Wpedantic -Werror)
target_include_directories(
  jingdu_native PRIVATE
  ${CMAKE_CURRENT_LIST_DIR}/../../../../../../core/native/include
)
find_library(log-lib log)
target_link_libraries(jingdu_native PRIVATE jingdu_core ${log-lib})

cmake_minimum_required(VERSION 3.16)
project(jingdu_core LANGUAGES CXX)

add_library(jingdu_core STATIC
  src/core_api_cached.cpp
  src/index_cache.cpp
  src/sha256.cpp
)
target_include_directories(jingdu_core PUBLIC include PRIVATE src)
target_compile_features(jingdu_core PUBLIC cxx_std_17)
target_compile_options(jingdu_core PRIVATE -Wall -Wextra -Wpedantic -Werror)
set_target_properties(jingdu_core PROPERTIES POSITION_INDEPENDENT_CODE ON)

option(JINGDU_BUILD_TESTS "Build Jingdu native core tests" ON)
if(JINGDU_BUILD_TESTS)
  enable_testing()
  find_package(Threads REQUIRED)

  add_executable(jingdu_core_test tests/core_api_test.cpp)
  target_link_libraries(jingdu_core_test PRIVATE jingdu_core)
  target_compile_options(jingdu_core_test PRIVATE -Wall -Wextra -Wpedantic -Werror)
  add_test(NAME jingdu_core_test COMMAND jingdu_core_test)

  add_executable(jingdu_core_stress_test tests/core_stress_test.cpp)
  target_link_libraries(jingdu_core_stress_test PRIVATE jingdu_core Threads::Threads)
  target_compile_options(jingdu_core_stress_test PRIVATE -Wall -Wextra -Wpedantic -Werror)
  add_test(NAME jingdu_core_stress_test COMMAND jingdu_core_stress_test)
  set_tests_properties(jingdu_core_stress_test PROPERTIES TIMEOUT 90)

  add_executable(jingdu_core_performance_gate_test tests/core_performance_gate_test.cpp)
  target_link_libraries(jingdu_core_performance_gate_test PRIVATE jingdu_core)
  target_compile_options(jingdu_core_performance_gate_test PRIVATE -Wall -Wextra -Wpedantic -Werror)
  add_test(NAME jingdu_core_performance_gate_test COMMAND jingdu_core_performance_gate_test)
  set_tests_properties(jingdu_core_performance_gate_test PROPERTIES TIMEOUT 120)

  # Near-1GiB bounded-memory contract: same behavior gate, much larger source, fixed RSS ceiling.
  add_test(NAME jingdu_core_near_1gib_rss_gate_test COMMAND jingdu_core_performance_gate_test)
  set_tests_properties(jingdu_core_near_1gib_rss_gate_test PROPERTIES
    ENVIRONMENT "JINGDU_PERF_FIXTURE_MIB=960"
    TIMEOUT 300)
endif()

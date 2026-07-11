cmake_minimum_required(VERSION 3.22)
project(tzf_tools LANGUAGES CXX)

add_library(tzf-core STATIC app/src/main/cpp/tzf_core.cpp)
target_include_directories(tzf-core PUBLIC app/src/main/cpp)
target_compile_features(tzf-core PUBLIC cxx_std_20)

find_package(libjpeg-turbo CONFIG QUIET)
if(TARGET libjpeg-turbo::turbojpeg)
    target_sources(tzf-core PRIVATE app/src/main/cpp/tzf_turbojpeg.cpp)
    target_link_libraries(tzf-core PUBLIC libjpeg-turbo::turbojpeg)
    target_compile_definitions(tzf-core PUBLIC TZF_HAS_TURBOJPEG=1)
endif()

add_executable(tzf-cli tools/tzf_cli.cpp)
target_link_libraries(tzf-cli PRIVATE tzf-core)

include(CTest)
if(BUILD_TESTING)
    add_executable(tzf-core-tests tests/tzf_core_tests.cpp)
    target_link_libraries(tzf-core-tests PRIVATE tzf-core)
    add_test(NAME tzf-core-tests COMMAND tzf-core-tests)
endif()

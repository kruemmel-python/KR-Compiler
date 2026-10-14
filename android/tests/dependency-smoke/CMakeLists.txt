cmake_minimum_required(VERSION 3.21)
project(kr_dependency_smoke LANGUAGES CXX)
find_package(fmt REQUIRED)
add_executable(kr_dependency_smoke main.cpp)
target_link_libraries(kr_dependency_smoke PRIVATE fmt::fmt)


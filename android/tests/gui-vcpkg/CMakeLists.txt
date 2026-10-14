cmake_minimum_required(VERSION 3.20)
project(KRGuiCrossTest LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(SDL3 CONFIG REQUIRED)

add_executable(KRGuiCrossTest WIN32 src/main.cpp)
target_link_libraries(KRGuiCrossTest PRIVATE SDL3::SDL3 imgui::imgui)

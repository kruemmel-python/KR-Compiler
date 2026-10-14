cmake_minimum_required(VERSION 3.22)
project(kr_runner LANGUAGES CXX)
add_library(kr_runner SHARED runner.cpp)
target_link_libraries(kr_runner PRIVATE dl log)

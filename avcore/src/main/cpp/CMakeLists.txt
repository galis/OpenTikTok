# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.10.2)

# Declares and names the project.

project("opentiktok")

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

set(CMAKE_CXX_STANDARD 11)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
file(GLOB_RECURSE srcfiles "*/*.cpp")
message(${srcfiles})
message("----------->" +${ANDROID_ABI})
link_directories(../../../libs/${ANDROID_ABI})
include_directories(include)
add_library(arcore SHARED ${srcfiles})
target_include_directories(arcore PUBLIC include)
target_link_libraries(
        arcore
        android
        log
        mediandk
        opencv_java3
        GLESv3
        EGL
        yuv
)
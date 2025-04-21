cmake_minimum_required(VERSION 3.10)
project(GameEngine)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Настройки для GLFW и GLEW
find_package(OpenGL REQUIRED)
find_package(glfw3 REQUIRED)
find_package(GLEW REQUIRED)

# Настройки для GLM
find_package(glm REQUIRED)



 set(IMGUI_DIR ${CMAKE_SOURCE_DIR}/ui/imgui-1.91.9b)
 add_library(imgui STATIC 
     ${IMGUI_DIR}/imgui.cpp
     ${IMGUI_DIR}/imgui_demo.cpp
     ${IMGUI_DIR}/imgui_draw.cpp
     ${IMGUI_DIR}/imgui_tables.cpp
     ${IMGUI_DIR}/imgui_widgets.cpp
     ${IMGUI_DIR}/backends/imgui_impl_glfw.cpp
     ${IMGUI_DIR}/backends/imgui_impl_opengl3.cpp
 )
 target_include_directories(imgui PUBLIC ${IMGUI_DIR} ${IMGUI_DIR}/backends)

# Исходные файлы проекта
set(SOURCE_FILES
    main.cpp
    scene.hpp
)

# Исполняемый файл
add_executable(GameEngine ${SOURCE_FILES})

# Подключение библиотек
target_link_libraries(GameEngine
    OpenGL::GL
    glfw
    GLEW::GLEW
    glm::glm
    imgui
)

# Дополнительные зависимости для разных платформ
if(WIN32)
    target_link_libraries(GameEngine opengl32)
endif()

if(APPLE)
    find_library(COCOA_LIBRARY Cocoa)
    find_library(IOKIT_LIBRARY IOKit)
    find_library(COREVIDEO_LIBRARY CoreVideo)
    target_link_libraries(GameEngine
        ${COCOA_LIBRARY} 
        ${IOKIT_LIBRARY} 
        ${COREVIDEO_LIBRARY})
endif()

if(UNIX AND NOT APPLE)
    target_link_libraries(GameEngine X11)
endif()

# Настройки компиляции
target_compile_options(GameEngine PRIVATE
    $<$<CXX_COMPILER_ID:MSVC>:/W4 /WX>
    $<$<NOT:$<CXX_COMPILER_ID:MSVC>>:-Wall -Wextra -Werror>
)

# Включение директорий для ImGui
target_include_directories(GameEngine PRIVATE
    ${IMGUI_DIR}
    ${IMGUI_DIR}/backends
)

# Копирование ресурсов (если нужно)
file(COPY ${CMAKE_SOURCE_DIR}/images.bmp DESTINATION ${CMAKE_BINARY_DIR})
file(COPY ${CMAKE_SOURCE_DIR}/fragment.glsl DESTINATION ${CMAKE_BINARY_DIR})
file(COPY ${CMAKE_SOURCE_DIR}/vertex.glsl DESTINATION ${CMAKE_BINARY_DIR})

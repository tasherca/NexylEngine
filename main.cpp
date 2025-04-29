#include <GL/glew.h>
#include <GLFW/glfw3.h>
#include <glm/glm.hpp>
#include <glm/gtc/matrix_transform.hpp>
#include <glm/gtc/type_ptr.hpp>
#include <stdio.h>
#include <stdlib.h>
#include <math.h>
#include <string.h>
#include <fstream>
#include <sstream>
#include <vector>
#include <imgui.h>
#include "ui/imgui-1.91.9b/backends/imgui_impl_glfw.h"
#include "ui/imgui-1.91.9b/backends/imgui_impl_opengl3.h"
#include "scene.hpp"

// Global variables for camera
float camPosX = 0.0f, camPosY = 2.0f, camPosZ = 5.0f;
float camYaw = 0.0f, camPitch = 0.0f;
float camSpeed = 5.0f;
float camRotSpeed = 0.1f;
float lastMouseX = 400.0f, lastMouseY = 300.0f;
bool firstMouse = true;
glm::vec3 cameraFront(0.0f, 0.0f, -1.0f);
bool cameraDirty = true;

// Global variables for VBO, VAO, and EBO
#define NUM_LODS 3
GLuint VAOs[NUM_LODS], VBOs[NUM_LODS], EBOs[NUM_LODS], instanceVBO;
unsigned int indexCounts[NUM_LODS];
GLuint shaderProgram;
GLuint matrixUBO;

// Uniform locations cache
struct {
    GLint isOutline;
    GLint outlineWidth;
    GLint material_diffuse;
    GLint material_specular;
    GLint material_shininess;
    GLint light_position;
    GLint light_color;
    GLint light_ambientStrength;
    GLint light_direction;
    GLint light_type;
    GLint viewPos;
    GLint time;
} uniforms;

// Gizmo VAO/VBO for different light types
GLuint gizmoVAO, gizmoVBO, gizmoEBO;
unsigned int gizmoIndexCount;
GLuint sphereVAO, sphereVBO, sphereEBO; // Для визуализации радиуса точечного света
unsigned int sphereIndexCount;

// Global variable for projection matrix and time
glm::mat4 projection = glm::mat4(1.0f);
int windowWidth = 800, windowHeight = 600;
float globalTime = 0.0f;

// Scene and object management
Scene scene;
int selectedObjectId = -1;
bool isRotating = false, isScaling = false, isTranslating = false;
bool isDragging = false;
float lastX = 0.0f, lastY = 0.0f;
bool sceneDirty = true;

// Forward declaration
int selectLOD(float distance);

// Read shader file
std::string readShaderFile(const char* filename) {
    std::ifstream file(filename);
    if (!file.is_open()) {
        printf("Failed to open shader file: %s\n", filename);
        return "";
    }
    std::stringstream buffer;
    buffer << file.rdbuf();
    return buffer.str();
}

// Load BMP texture
GLuint loadTexture(const char* filename) {
    FILE* file = fopen(filename, "rb");
    if (!file) {
        printf("Failed to open texture file: %s\n", filename);
        return 0;
    }

    unsigned char header[54];
    if (fread(header, 1, 54, file) != 54) {
        fclose(file);
        return 0;
    }

    int width = *(int*)&header[18];
    int height = *(int*)&header[22];
    int imageSize = *(int*)&header[34];

    unsigned char* data = new unsigned char[imageSize];
    if (fread(data, 1, imageSize, file) != (size_t)imageSize) {
        delete[] data;
        fclose(file);
        return 0;
    }
    fclose(file);

    GLuint textureID;
    glGenTextures(1, &textureID);
    glBindTexture(GL_TEXTURE_2D, textureID);

    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, GL_BGR, GL_UNSIGNED_BYTE, data);

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glGenerateMipmap(GL_TEXTURE_2D);

    delete[] data;
    return textureID;
}

// Create shader program
GLuint createShaderProgram(const char* vertexFile, const char* fragmentFile) {
    std::string vertexShaderCode = readShaderFile(vertexFile);
    std::string fragmentShaderCode = readShaderFile(fragmentFile);

    if (vertexShaderCode.empty() || fragmentShaderCode.empty()) {
        printf("Error reading shader files\n");
        return 0;
    }

    const char* vertexShaderSource = vertexShaderCode.c_str();
    const char* fragmentShaderSource = fragmentShaderCode.c_str();

    GLuint vertexShader = glCreateShader(GL_VERTEX_SHADER);
    glShaderSource(vertexShader, 1, &vertexShaderSource, NULL);
    glCompileShader(vertexShader);

    GLint success;
    char infoLog[512];
    glGetShaderiv(vertexShader, GL_COMPILE_STATUS, &success);
    if (!success) {
        glGetShaderInfoLog(vertexShader, 512, NULL, infoLog);
        printf("Vertex shader compilation error: %s\n", infoLog);
        glDeleteShader(vertexShader);
        return 0;
    }

    GLuint fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
    glShaderSource(fragmentShader, 1, &fragmentShaderSource, NULL);
    glCompileShader(fragmentShader);

    glGetShaderiv(fragmentShader, GL_COMPILE_STATUS, &success);
    if (!success) {
        glGetShaderInfoLog(fragmentShader, 512, NULL, infoLog);
        printf("Fragment shader compilation error: %s\n", infoLog);
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        return 0;
    }

    GLuint shaderProgram = glCreateProgram();
    glAttachShader(shaderProgram, vertexShader);
    glAttachShader(shaderProgram, fragmentShader);
    glLinkProgram(shaderProgram);

    glGetProgramiv(shaderProgram, GL_LINK_STATUS, &success);
    if (!success) {
        glGetProgramInfoLog(shaderProgram, 512, NULL, infoLog);
        printf("Shader program linking error: %s\n", infoLog);
        glDeleteShader(vertexShader);
        glDeleteShader(fragmentShader);
        glDeleteProgram(shaderProgram);
        return 0;
    }

    glDeleteShader(vertexShader);
    glDeleteShader(fragmentShader);

    uniforms.isOutline = glGetUniformLocation(shaderProgram, "isOutline");
    uniforms.outlineWidth = glGetUniformLocation(shaderProgram, "outlineWidth");
    uniforms.material_diffuse = glGetUniformLocation(shaderProgram, "material_diffuse");
    uniforms.material_specular = glGetUniformLocation(shaderProgram, "material_specular");
    uniforms.material_shininess = glGetUniformLocation(shaderProgram, "material_shininess");
    uniforms.light_position = glGetUniformLocation(shaderProgram, "light_position");
    uniforms.light_color = glGetUniformLocation(shaderProgram, "light_color");
    uniforms.light_ambientStrength = glGetUniformLocation(shaderProgram, "light_ambientStrength");
    uniforms.light_direction = glGetUniformLocation(shaderProgram, "light_direction");
    uniforms.light_type = glGetUniformLocation(shaderProgram, "light_type");
    uniforms.viewPos = glGetUniformLocation(shaderProgram, "viewPos");
    uniforms.time = glGetUniformLocation(shaderProgram, "time");

    return shaderProgram;
}

// Initialize matrix UBO
void initMatrixUBO() {
    glGenBuffers(1, &matrixUBO);
    glBindBuffer(GL_UNIFORM_BUFFER, matrixUBO);
    glBufferData(GL_UNIFORM_BUFFER, 2 * sizeof(glm::mat4), NULL, GL_DYNAMIC_DRAW);
    glBindBuffer(GL_UNIFORM_BUFFER, 0);
    glBindBufferBase(GL_UNIFORM_BUFFER, 0, matrixUBO);
}

// Initialize cube VBO and VAO for LOD
void initCubeVBO(int lod) {
    float vertices_high[] = {
        -0.5f, -0.5f,  0.5f,  0.0f, 0.0f,  0.0f,  0.0f,  1.0f,
         0.5f, -0.5f,  0.5f,  1.0f, 0.0f,  0.0f,  0.0f,  1.0f,
         0.5f,  0.5f,  0.5f,  1.0f, 1.0f,  0.0f,  0.0f,  1.0f,
        -0.5f,  0.5f,  0.5f,  0.0f, 1.0f,  0.0f,  0.0f,  1.0f,
        -0.5f, -0.5f, -0.5f,  1.0f, 0.0f,  0.0f,  0.0f, -1.0f,
         0.5f, -0.5f, -0.5f,  0.0f, 0.0f,  0.0f,  0.0f, -1.0f,
         0.5f,  0.5f, -0.5f,  0.0f, 1.0f,  0.0f,  0.0f, -1.0f,
        -0.5f,  0.5f, -0.5f,  1.0f, 1.0f,  0.0f,  0.0f, -1.0f,
        -0.5f, -0.5f, -0.5f,  0.0f, 0.0f, -1.0f,  0.0f,  0.0f,
        -0.5f, -0.5f,  0.5f,  1.0f, 0.0f, -1.0f,  0.0f,  0.0f,
        -0.5f,  0.5f,  0.5f,  1.0f, 1.0f, -1.0f,  0.0f,  0.0f,
        -0.5f,  0.5f, -0.5f,  0.0f, 1.0f, -1.0f,  0.0f,  0.0f,
         0.5f, -0.5f,  0.5f,  0.0f, 0.0f,  1.0f,  0.0f,  0.0f,
         0.5f, -0.5f, -0.5f,  1.0f, 0.0f,  1.0f,  0.0f,  0.0f,
         0.5f,  0.5f, -0.5f,  1.0f, 1.0f,  1.0f,  0.0f,  0.0f,
         0.5f,  0.5f,  0.5f,  0.0f, 1.0f,  1.0f,  0.0f,  0.0f,
        -0.5f,  0.5f,  0.5f,  0.0f, 1.0f,  0.0f,  1.0f,  0.0f,
         0.5f,  0.5f,  0.5f,  1.0f, 1.0f,  0.0f,  1.0f,  0.0f,
         0.5f,  0.5f, -0.5f,  1.0f, 0.0f,  0.0f,  1.0f,  0.0f,
        -0.5f,  0.5f, -0.5f,  0.0f, 0.0f,  0.0f,  1.0f,  0.0f,
        -0.5f, -0.5f, -0.5f,  0.0f, 1.0f,  0.0f, -1.0f,  0.0f,
         0.5f, -0.5f, -0.5f,  1.0f, 1.0f,  0.0f, -1.0f,  0.0f,
         0.5f, -0.5f,  0.5f,  1.0f, 0.0f,  0.0f, -1.0f,  0.0f,
        -0.5f, -0.5f,  0.5f,  0.0f, 0.0f,  0.0f, -1.0f,  0.0f
    };

    unsigned int indices_high[] = {
        0,  1,  2,   2,  3,  0,
        4,  5,  6,   6,  7,  4,
        8,  9, 10,  10, 11,  8,
        12, 13, 14,  14, 15, 12,
        16, 17, 18,  18, 19, 16,
        20, 21, 22,  22, 23, 20
    };

    float vertices_medium[] = {
        -0.5f, -0.5f, 0.5f,  0.0f, 0.0f,  0.0f,  0.0f,  1.0f,
         0.5f, -0.5f, 0.5f,  1.0f, 0.0f,  0.0f,  0.0f,  1.0f,
         0.5f,  0.5f, 0.5f,  1.0f, 1.0f,  0.0f,  0.0f,  1.0f,
        -0.5f,  0.5f, 0.5f,  0.0f, 1.0f,  0.0f,  0.0f,  1.0f,
        -0.5f, -0.5f, -0.5f,  1.0f, 0.0f,  0.0f,  0.0f, -1.0f,
         0.5f, -0.5f, -0.5f,  0.0f, 0.0f,  0.0f,  0.0f, -1.0f,
         0.5f,  0.5f, -0.5f,  0.0f, 1.0f,  0.0f,  0.0f, -1.0f,
        -0.5f,  0.5f, -0.5f,  1.0f, 1.0f,  0.0f,  0.0f, -1.0f,
        -0.5f, -0.5f, -0.5f,  0.0f, 0.0f, -1.0f,  0.0f,  0.0f,
        -0.5f, -0.5f, 0.5f,  1.0f, 0.0f, -1.0f,  0.0f,  0.0f,
        -0.5f,  0.5f, 0.5f,  1.0f, 1.0f, -1.0f,  0.0f,  0.0f,
        -0.5f,  0.5f, -0.5f,  0.0f, 1.0f, -1.0f,  0.0f,  0.0f,
         0.5f, -0.5f, 0.5f,  0.0f, 0.0f,  1.0f,  0.0f,  0.0f,
         0.5f, -0.5f, -0.5f,  1.0f, 0.0f,  1.0f,  0.0f,  0.0f,
         0.5f,  0.5f, -0.5f,  1.0f, 1.0f,  1.0f,  0.0f,  0.0f,
         0.5f,  0.5f, 0.5f,  0.0f, 1.0f,  1.0f,  0.0f,  0.0f,
        -0.5f,  0.5f, 0.5f,  0.0f, 1.0f,  0.0f,  1.0f,  0.0f,
         0.5f,  0.5f, 0.5f,  1.0f, 1.0f,  0.0f,  1.0f,  0.0f,
         0.5f,  0.5f, -0.5f,  1.0f, 0.0f,  0.0f,  1.0f,  0.0f,
        -0.5f,  0.5f, -0.5f,  0.0f, 0.0f,  0.0f,  1.0f,  0.0f,
        -0.5f, -0.5f, -0.5f,  0.0f, 1.0f,  0.0f, -1.0f,  0.0f,
         0.5f, -0.5f, -0.5f,  1.0f, 1.0f,  0.0f, -1.0f,  0.0f,
         0.5f, -0.5f, 0.5f,  1.0f, 0.0f,  0.0f, -1.0f,  0.0f,
        -0.5f, -0.5f, 0.5f,  0.0f, 0.0f,  0.0f, -1.0f,  0.0f
    };

    unsigned int indices_medium[] = {
        0,  1,  2,   2,  3,  0,
        4,  5,  6,   6,  7,  4,
        8,  9, 10,  10, 11,  8,
        12, 13, 14,  14, 15, 12,
        16, 17, 18,  18, 19, 16,
        20, 21, 22,  22, 23, 20
    };

    float vertices_low[] = {
        -0.5f, -0.5f, -0.5f,  0.0f, 0.0f,  0.0f,  0.0f, -1.0f,
         0.5f, -0.5f, -0.5f,  1.0f, 0.0f,  0.0f,  0.0f, -1.0f,
         0.5f, -0.5f,  0.5f,  1.0f, 1.0f,  0.0f,  0.0f,  1.0f,
        -0.5f, -0.5f,  0.5f,  0.0f, 1.0f,  0.0f,  0.0f,  1.0f,
        -0.5f,  0.5f, -0.5f,  0.0f, 0.0f,  0.0f,  0.0f, -1.0f,
         0.5f,  0.5f, -0.5f,  1.0f, 0.0f,  0.0f,  0.0f, -1.0f,
         0.5f,  0.5f,  0.5f,  1.0f, 1.0f,  0.0f,  0.0f,  1.0f,
        -0.5f,  0.5f,  0.5f,  0.0f, 1.0f,  0.0f,  0.0f,  1.0f
    };

    unsigned int indices_low[] = {
        3, 2, 6,   6, 7, 3,
        0, 1, 5,   5, 4, 0,
        0, 4, 7,   7, 3, 0,
        1, 2, 6,   6, 5, 1,
        4, 5, 6,   6, 7, 4,
        0, 1, 2,   2, 3, 0
    };

    float* vertices[] = { vertices_high, vertices_medium, vertices_low };
    unsigned int* indices[] = { indices_high, indices_medium, indices_low };
    size_t vertexSizes[] = { sizeof(vertices_high), sizeof(vertices_medium), sizeof(vertices_low) };
    size_t indexSizes[] = { sizeof(indices_high), sizeof(indices_medium), sizeof(indices_low) };

    indexCounts[lod] = indexSizes[lod] / sizeof(unsigned int);

    glGenVertexArrays(1, &VAOs[lod]);
    glBindVertexArray(VAOs[lod]);

    glGenBuffers(1, &VBOs[lod]);
    glBindBuffer(GL_ARRAY_BUFFER, VBOs[lod]);
    glBufferData(GL_ARRAY_BUFFER, vertexSizes[lod], vertices[lod], GL_STATIC_DRAW);

    glGenBuffers(1, &EBOs[lod]);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, EBOs[lod]);
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, indexSizes[lod], indices[lod], GL_STATIC_DRAW);

    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 8 * sizeof(float), (void*)0);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 8 * sizeof(float), (void*)(3 * sizeof(float)));
    glEnableVertexAttribArray(1);
    glVertexAttribPointer(2, 3, GL_FLOAT, GL_FALSE, 8 * sizeof(float), (void*)(5 * sizeof(float)));
    glEnableVertexAttribArray(2);

    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindVertexArray(0);
}

// Initialize instance VBO
void initInstanceVBO() {
    glGenBuffers(1, &instanceVBO);
    glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
    glBufferData(GL_ARRAY_BUFFER, 0, nullptr, GL_DYNAMIC_DRAW);
    glBindBuffer(GL_ARRAY_BUFFER, 0);
}

// Update instance VBO
void updateInstanceVBO(int lod, bool renderLights = false) {
    std::vector<glm::mat4> modelMatrices;
    std::vector<float> selections;
    std::vector<float> isLightSources;
    std::vector<float> lightIntensities;
    glm::vec3 camPos(camPosX, camPosY, camPosZ);

    for (const auto& obj : scene.getObjects()) {
        if (!obj.isVisible) continue;
        if (renderLights && (obj.type != POINT_LIGHT && obj.type != DIRECTIONAL_LIGHT && obj.type != AMBIENT_LIGHT)) continue;
        if (!renderLights && obj.type != CUBE) continue;
        float distance = glm::length(obj.position - camPos);
        int selectedLOD = selectLOD(distance);
        if (selectedLOD != lod) continue;

        glm::mat4 model = glm::mat4(1.0f);
        model = glm::translate(model, obj.position);

        // Визуальные отличия источников света
        if (obj.type == CUBE || obj.type == POINT_LIGHT || obj.type == DIRECTIONAL_LIGHT || obj.type == AMBIENT_LIGHT) {
            model = glm::rotate(model, glm::radians(obj.rotation.x), glm::vec3(1.0f, 0.0f, 0.0f));
            model = glm::rotate(model, glm::radians(obj.rotation.y), glm::vec3(0.0f, 1.0f, 0.0f));
            float scale = obj.scale;
            if (obj.type == POINT_LIGHT) {
                scale = 0.15f; // Точечный свет - маленький куб
            } else if (obj.type == DIRECTIONAL_LIGHT) {
                scale = 0.2f; // Направленный свет - стрелка
                glm::vec3 dir = glm::normalize(obj.lightDirection);
                glm::vec3 up = glm::vec3(0.0f, 1.0f, 0.0f);
                if (glm::abs(glm::dot(dir, up)) > 0.99f) up = glm::vec3(0.0f, 0.0f, 1.0f);
                glm::vec3 right = glm::normalize(glm::cross(up, dir));
                up = glm::cross(dir, right);
                glm::mat4 rotation = glm::mat4(
                    glm::vec4(right, 0.0f),
                    glm::vec4(up, 0.0f),
                    glm::vec4(dir, 0.0f),
                    glm::vec4(0.0f, 0.0f, 0.0f, 1.0f)
                );
                model = model * rotation;
            } else if (obj.type == AMBIENT_LIGHT) {
                // Пульсация для окружающего света при выделении
                scale = (selectedObjectId == obj.id) ? 0.2f + 0.05f * sin(globalTime * 2.0f) : 0.2f;
            }
            model = glm::scale(model, glm::vec3(scale));
        }
        modelMatrices.push_back(model);
        selections.push_back(obj.id == selectedObjectId ? 1.0f : 0.0f);
        isLightSources.push_back((obj.type == POINT_LIGHT || obj.type == DIRECTIONAL_LIGHT || obj.type == AMBIENT_LIGHT) ? 1.0f : 0.0f);
        lightIntensities.push_back(obj.lightIntensity);
    }

    if (!modelMatrices.empty()) {
        glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
        glBufferData(GL_ARRAY_BUFFER, modelMatrices.size() * (sizeof(glm::mat4) + 3 * sizeof(float)), nullptr, GL_DYNAMIC_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0, modelMatrices.size() * sizeof(glm::mat4), modelMatrices.data());
        glBufferSubData(GL_ARRAY_BUFFER, modelMatrices.size() * sizeof(glm::mat4), selections.size() * sizeof(float), selections.data());
        glBufferSubData(GL_ARRAY_BUFFER, modelMatrices.size() * (sizeof(glm::mat4) + sizeof(float)), isLightSources.size() * sizeof(float), isLightSources.data());
        glBufferSubData(GL_ARRAY_BUFFER, modelMatrices.size() * (sizeof(glm::mat4) + 2 * sizeof(float)), lightIntensities.size() * sizeof(float), lightIntensities.data());

        glBindVertexArray(VAOs[lod]);
        glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
        for (int i = 0; i < 4; i++) {
            glVertexAttribPointer(3 + i, 4, GL_FLOAT, GL_FALSE, sizeof(glm::mat4), (void*)(i * sizeof(glm::vec4)));
            glEnableVertexAttribArray(3 + i);
            glVertexAttribDivisor(3 + i, 1);
        }
        glVertexAttribPointer(7, 1, GL_FLOAT, GL_FALSE, sizeof(float), (void*)(modelMatrices.size() * sizeof(glm::mat4)));
        glEnableVertexAttribArray(7);
        glVertexAttribDivisor(7, 1);
        glVertexAttribPointer(8, 1, GL_FLOAT, GL_FALSE, sizeof(float), (void*)(modelMatrices.size() * (sizeof(glm::mat4) + sizeof(float))));
        glEnableVertexAttribArray(8);
        glVertexAttribDivisor(8, 1);
        glVertexAttribPointer(9, 1, GL_FLOAT, GL_FALSE, sizeof(float), (void*)(modelMatrices.size() * (sizeof(glm::mat4) + 2 * sizeof(float))));
        glEnableVertexAttribArray(9);
        glVertexAttribDivisor(9, 1);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }
}

// Initialize gizmo VBO/VAO (for directional light and cube gizmos)
void initGizmoVBO() {
    float gizmoVertices[] = {
        // Оси для куба и направленного света
        0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, // X ось (красная)
        1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, // Y ось (зелёная)
        0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f,
        0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 2.0f, // Z ось (синяя)
        0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 2.0f,
        // Стрелка для направленного света
        0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 3.0f, // Начало стрелки
        0.0f, 0.0f, 2.0f, 1.0f, 1.0f, 0.0f, 0.0f, 3.0f, // Конец стрелки
        0.0f, 0.0f, 2.0f, 1.0f, 1.0f, 0.0f, 0.0f, 3.0f,
        0.2f, 0.0f, 1.5f, 1.0f, 1.0f, 0.0f, 0.0f, 3.0f,
        0.0f, 0.0f, 2.0f, 1.0f, 1.0f, 0.0f, 0.0f, 3.0f,
        -0.2f, 0.0f, 1.5f, 1.0f, 1.0f, 0.0f, 0.0f, 3.0f
    };

    unsigned int gizmoIndices[] = {
        0, 1,  // X ось
        2, 3,  // Y ось
        4, 5,  // Z ось
        6, 7,  // Стрелка (основная линия)
        8, 9,  // Стрелка (правая часть)
        10, 11 // Стрелка (левая часть)
    };

    gizmoIndexCount = sizeof(gizmoIndices) / sizeof(unsigned int);

    glGenVertexArrays(1, &gizmoVAO);
    glBindVertexArray(gizmoVAO);

    glGenBuffers(1, &gizmoVBO);
    glBindBuffer(GL_ARRAY_BUFFER, gizmoVBO);
    glBufferData(GL_ARRAY_BUFFER, sizeof(gizmoVertices), gizmoVertices, GL_STATIC_DRAW);

    glGenBuffers(1, &gizmoEBO);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, gizmoEBO);
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, sizeof(gizmoIndices), gizmoIndices, GL_STATIC_DRAW);

    // Привязка атрибутов для гизмо
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 8 * sizeof(float), (void*)0); // Позиция
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(2, 3, GL_FLOAT, GL_FALSE, 8 * sizeof(float), (void*)(3 * sizeof(float))); // Цвет
    glEnableVertexAttribArray(2);
    glVertexAttribPointer(3, 1, GL_FLOAT, GL_FALSE, 8 * sizeof(float), (void*)(7 * sizeof(float))); // GizmoType
    glEnableVertexAttribArray(3);

    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindVertexArray(0);
}

// Initialize sphere VBO/VAO (for point light radius visualization)
void initSphereVBO() {
    std::vector<float> vertices;
    std::vector<unsigned int> indices;

    const int stacks = 16;
    const int slices = 16;
    const float radius = 1.0f;

    for (int i = 0; i <= stacks; ++i) {
        float phi = M_PI * (float)i / stacks;
        for (int j = 0; j <= slices; ++j) {
            float theta = 2.0f * M_PI * (float)j / slices;

            float x = radius * sin(phi) * cos(theta);
            float y = radius * sin(phi) * sin(theta);
            float z = radius * cos(phi);

            // Position
            vertices.push_back(x);
            vertices.push_back(y);
            vertices.push_back(z);
            // Color (white for wireframe)
            vertices.push_back(1.0f);
            vertices.push_back(1.0f);
            vertices.push_back(1.0f);
            // Gizmo type
            vertices.push_back(4.0f);
        }
    }

    for (int i = 0; i < stacks; ++i) {
        for (int j = 0; j < slices; ++j) {
            int k1 = i * (slices + 1) + j;
            int k2 = k1 + slices + 1;

            indices.push_back(k1);
            indices.push_back(k2);

            indices.push_back(k1);
            indices.push_back(k1 + 1);
        }
    }

    sphereIndexCount = indices.size();

    glGenVertexArrays(1, &sphereVAO);
    glBindVertexArray(sphereVAO);

    glGenBuffers(1, &sphereVBO);
    glBindBuffer(GL_ARRAY_BUFFER, sphereVBO);
    glBufferData(GL_ARRAY_BUFFER, vertices.size() * sizeof(float), vertices.data(), GL_STATIC_DRAW);

    glGenBuffers(1, &sphereEBO);
    glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, sphereEBO);
    glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices.size() * sizeof(unsigned int), indices.data(), GL_STATIC_DRAW);

    // Привязка атрибутов для сферы
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 7 * sizeof(float), (void*)0); // Позиция
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(2, 3, GL_FLOAT, GL_FALSE, 7 * sizeof(float), (void*)(3 * sizeof(float))); // Цвет
    glEnableVertexAttribArray(2);
    glVertexAttribPointer(3, 1, GL_FLOAT, GL_FALSE, 7 * sizeof(float), (void*)(6 * sizeof(float))); // GizmoType
    glEnableVertexAttribArray(3);

    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindVertexArray(0);
}

// Select LOD based on distance
int selectLOD(float distance) {
    if (distance < 5.0f) return 0;
    if (distance < 15.0f) return 1;
    return 2;
}

// Framebuffer size callback
void framebufferSizeCallback(GLFWwindow* /*window*/, int width, int height) {
    glViewport(0, 0, width, height);
    windowWidth = width;
    windowHeight = height;
    projection = glm::perspective(glm::radians(45.0f), (float)width / height, 0.1f, 100.0f);
    glBindBuffer(GL_UNIFORM_BUFFER, matrixUBO);
    glBufferSubData(GL_UNIFORM_BUFFER, 0, sizeof(glm::mat4), glm::value_ptr(projection));
    glBindBuffer(GL_UNIFORM_BUFFER, 0);
}

// Global deltaTime for keyCallback
float globalDeltaTime = 0.016f;

// Key callback
void keyCallback(GLFWwindow* window, int key, int /*scancode*/, int action, int mods) {
    if (key == GLFW_KEY_ESCAPE && action == GLFW_PRESS) {
        glfwSetWindowShouldClose(window, GLFW_TRUE);
        return;
    }
    if (action == GLFW_PRESS && key == GLFW_KEY_DELETE && selectedObjectId != -1) {
        if (scene.removeObject(selectedObjectId)) {
            selectedObjectId = -1;
            isRotating = isScaling = isTranslating = false;
            sceneDirty = true;
        }
        return;
    }

    if (mods == GLFW_MOD_CONTROL && action == GLFW_PRESS) {
        isRotating = isScaling = isTranslating = false;
        if (key == GLFW_KEY_R) isRotating = true;
        else if (key == GLFW_KEY_S) isScaling = true;
        else if (key == GLFW_KEY_T) isTranslating = true;
        sceneDirty = true;
    }

    float deltaTime = globalDeltaTime;
    glm::vec3 right = glm::normalize(glm::cross(cameraFront, glm::vec3(0.0f, 1.0f, 0.0f)));

    if (action == GLFW_PRESS || action == GLFW_REPEAT) {
        if (key == GLFW_KEY_W) {
            camPosX += camSpeed * deltaTime * cameraFront.x;
            camPosY += camSpeed * deltaTime * cameraFront.y;
            camPosZ += camSpeed * deltaTime * cameraFront.z;
        }
        else if (key == GLFW_KEY_S) {
            camPosX -= camSpeed * deltaTime * cameraFront.x;
            camPosY -= camSpeed * deltaTime * cameraFront.y;
            camPosZ -= camSpeed * deltaTime * cameraFront.z;
        }
        else if (key == GLFW_KEY_A) {
            camPosX -= camSpeed * deltaTime * right.x;
            camPosZ -= camSpeed * deltaTime * right.z;
        }
        else if (key == GLFW_KEY_D) {
            camPosX += camSpeed * deltaTime * right.x;
            camPosZ += camSpeed * deltaTime * right.z;
        }
        else if (key == GLFW_KEY_SPACE) {
            camPosY += camSpeed * deltaTime;
        }
        else if (key == GLFW_KEY_LEFT_CONTROL) {
            camPosY -= camSpeed * deltaTime;
        }
        cameraDirty = true;
        sceneDirty = true;
    }
}

// Mouse move callback
void mouseMoveCallback(GLFWwindow* window, double xpos, double ypos) {
    if (isDragging && selectedObjectId != -1) {
        int width, height;
        glfwGetWindowSize(window, &width, &height);
        float x = (xpos / width) * 2 - 1;
        float y = -((ypos / height) * 2 - 1);

        SceneObject* obj = scene.getObject(selectedObjectId);
        if (obj) {
            if (isRotating && obj->type == CUBE) {
                obj->rotation.x += (y - lastY) * 180.0f;
                obj->rotation.y += (x - lastX) * 180.0f;
            }
            else if (isScaling && obj->type == CUBE) {
                obj->scale += (y - lastY) * 2.0f;
                if (obj->scale < 0.1f) obj->scale = 0.1f;
                if (obj->scale > 2.0f) obj->scale = 2.0f;
            }
            else if (isTranslating) {
                obj->position.x += (x - lastX);
                obj->position.y += (y - lastY);
            }
            sceneDirty = true;
        }
        lastX = x;
        lastY = y;
    }
    else if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS) {
        if (firstMouse) {
            lastMouseX = xpos;
            lastMouseY = ypos;
            firstMouse = false;
        }

        float xoffset = xpos - lastMouseX;
        float yoffset = lastMouseY - ypos;
        lastMouseX = xpos;
        lastMouseY = ypos;

        xoffset *= camRotSpeed;
        yoffset *= camRotSpeed;

        camYaw += xoffset;
        camPitch += yoffset;

        if (camPitch > 89.0f) camPitch = 89.0f;
        if (camPitch < -89.0f) camPitch = -89.0f;

        cameraDirty = true;
        sceneDirty = true;
    }
    else {
        firstMouse = true;
    }
}

// Mouse button callback
void mouseButtonCallback(GLFWwindow* window, int button, int action, int /*mods*/) {
    if (button == GLFW_MOUSE_BUTTON_LEFT) {
        if (action == GLFW_PRESS) {
            bool isOverImGui = ImGui::GetIO().WantCaptureMouse;

            isDragging = true;
            double xpos, ypos;
            glfwGetCursorPos(window, &xpos, &ypos);
            int width, height;
            glfwGetWindowSize(window, &width, &height);
            lastX = (xpos / width) * 2 - 1;
            lastY = -((ypos / height) * 2 - 1);

            if (!isOverImGui && !isRotating && !isScaling && !isTranslating) {
                float minDist = FLT_MAX;
                int closestObjectId = -1;

                for (const auto& obj : scene.getObjects()) {
                    if (!obj.isVisible) continue;
                    glm::vec3 screenPos = glm::project(
                        obj.position,
                        glm::lookAt(glm::vec3(camPosX, camPosY, camPosZ),
                            glm::vec3(camPosX, camPosY, camPosZ) + cameraFront,
                            glm::vec3(0.0f, 1.0f, 0.0f)),
                        projection,
                        glm::vec4(0, 0, width, height)
                    );
                    float dist = glm::distance(glm::vec2(screenPos), glm::vec2(xpos, height - ypos));
                    float clickRadius = 30.0f; // Базовый радиус клика
                    if (obj.type == POINT_LIGHT || obj.type == DIRECTIONAL_LIGHT || obj.type == AMBIENT_LIGHT) {
                        clickRadius = 50.0f; // Увеличенный радиус для источников света
                    }
                    if (dist < clickRadius && dist < minDist) {
                        minDist = dist;
                        closestObjectId = obj.id;
                    }
                }

                selectedObjectId = closestObjectId;
                if (selectedObjectId == -1) {
                    isRotating = isScaling = isTranslating = false;
                }
                sceneDirty = true;
            }
        }
        else if (action == GLFW_RELEASE) {
            isDragging = false;
        }
    }
}

// Draw ImGui panel
void drawImGui() {
    ImGui_ImplOpenGL3_NewFrame();
    ImGui_ImplGlfw_NewFrame();
    ImGui::NewFrame();

    ImGuiWindowFlags window_flags = ImGuiWindowFlags_NoCollapse | ImGuiWindowFlags_NoMove;
    ImGui::SetNextWindowPos(ImVec2(0, 0), ImGuiCond_Always);
    ImGui::SetNextWindowSize(ImVec2(windowWidth * 0.25f, windowHeight), ImGuiCond_Always);
    ImGui::Begin("Scene Hierarchy", nullptr, window_flags);

    ImGui::Text("Scene Objects:");
    ImGui::Separator();

    for (const auto& obj : scene.getObjects()) {
        std::string label = obj.name + " (ID: " + std::to_string(obj.id) + ")";
        if (ImGui::Selectable(label.c_str(), selectedObjectId == obj.id)) {
            selectedObjectId = obj.id;
            isRotating = isScaling = isTranslating = false;
            sceneDirty = true;
        }
        if (selectedObjectId == obj.id) {
            ImGui::Text("Properties:");
            float pos[3] = { obj.position.x, obj.position.y, obj.position.z };
            if (ImGui::DragFloat3("Position", pos, 0.1f)) {
                scene.updateObjectPosition(obj.id, glm::vec3(pos[0], pos[1], pos[2]));
                sceneDirty = true;
            }
            if (obj.type == CUBE) {
                float rot[2] = { obj.rotation.x, obj.rotation.y };
                if (ImGui::DragFloat2("Rotation", rot, 1.0f)) {
                    scene.updateObjectRotation(obj.id, glm::vec2(rot[0], rot[1]));
                    sceneDirty = true;
                }
                float scale = obj.scale;
                if (ImGui::DragFloat("Scale", &scale, 0.01f, 0.1f, 2.0f)) {
                    scene.updateObjectScale(obj.id, scale);
                    sceneDirty = true;
                }
            }
            else {
                float color[3] = { obj.lightColor.x, obj.lightColor.y, obj.lightColor.z };
                if (ImGui::ColorEdit3("Light Color", color)) {
                    scene.getObject(obj.id)->lightColor = glm::vec3(color[0], color[1], color[2]);
                    sceneDirty = true;
                }
                float intensity = obj.lightIntensity;
                if (obj.type == POINT_LIGHT) {
                    if (ImGui::DragFloat("Intensity (Radius)", &intensity, 0.01f, 0.0f, 10.0f)) {
                        scene.getObject(obj.id)->lightIntensity = intensity;
                        sceneDirty = true;
                    }
                } else {
                    if (ImGui::DragFloat("Intensity", &intensity, 0.01f, 0.0f, 10.0f)) {
                        scene.getObject(obj.id)->lightIntensity = intensity;
                        sceneDirty = true;
                    }
                }
                if (obj.type == DIRECTIONAL_LIGHT) {
                    float dir[3] = { obj.lightDirection.x, obj.lightDirection.y, obj.lightDirection.z };
                    if (ImGui::DragFloat3("Direction", dir, 0.1f)) {
                        glm::vec3 newDir = glm::normalize(glm::vec3(dir[0], dir[1], dir[2]));
                        scene.getObject(obj.id)->lightDirection = newDir;
                        sceneDirty = true;
                    }
                }
            }
            ImGui::Separator();
        }
    }

    if (ImGui::IsWindowHovered() && ImGui::IsMouseClicked(ImGuiMouseButton_Right)) {
        ImGui::OpenPopup("SceneContextMenu");
    }

    if (ImGui::BeginPopup("SceneContextMenu")) {
        if (ImGui::MenuItem("Add Cube")) {
            scene.addObject("Cube_" + std::to_string(scene.getObjects().size() + 1), glm::vec3(0.0f), glm::vec2(0.0f), 0.5f);
            sceneDirty = true;
        }
        if (ImGui::MenuItem("Add Ambient Light")) {
            SceneObject light;
            light.name = "AmbientLight_" + std::to_string(scene.getObjects().size() + 1);
            light.type = AMBIENT_LIGHT;
            light.lightColor = glm::vec3(1.0f);
            light.lightIntensity = 0.2f;
            scene.addLight(light);
            sceneDirty = true;
        }
        if (ImGui::MenuItem("Add Point Light")) {
            SceneObject light;
            light.name = "PointLight_" + std::to_string(scene.getObjects().size() + 1);
            light.type = POINT_LIGHT;
            light.position = glm::vec3(0.0f, 1.0f, 0.0f);
            light.lightColor = glm::vec3(1.0f);
            light.lightIntensity = 1.0f;
            scene.addLight(light);
            sceneDirty = true;
        }
        if (ImGui::MenuItem("Add Directional Light")) {
            SceneObject light;
            light.name = "DirectionalLight_" + std::to_string(scene.getObjects().size() + 1);
            light.type = DIRECTIONAL_LIGHT;
            light.lightDirection = glm::vec3(0.0f, -1.0f, 0.0f);
            light.lightColor = glm::vec3(1.0f);
            light.lightIntensity = 1.0f;
            scene.addLight(light);
            sceneDirty = true;
        }
        ImGui::EndPopup();
    }

    ImGui::End();
    ImGui::Render();
    ImGui_ImplOpenGL3_RenderDrawData(ImGui::GetDrawData());
}

// Draw objects
void drawObjects(int lod, bool isOutlinePass, bool renderLights = false) {
    glUniform1i(uniforms.isOutline, isOutlinePass ? 1 : 0);
    glUniform1f(uniforms.outlineWidth, 0.2f);
    glUniform1f(uniforms.time, globalTime);
    updateInstanceVBO(lod, renderLights);
    glBindVertexArray(VAOs[lod]);
    std::vector<glm::mat4> modelMatrices;
    glm::vec3 camPos(camPosX, camPosY, camPosZ);
    for (const auto& obj : scene.getObjects()) {
        if (!obj.isVisible) continue;
        if (renderLights && (obj.type != POINT_LIGHT && obj.type != DIRECTIONAL_LIGHT && obj.type != AMBIENT_LIGHT)) continue;
        if (!renderLights && obj.type != CUBE) continue;
        float distance = glm::length(obj.position - camPos);
        if (selectLOD(distance) == lod) {
            glm::mat4 model = glm::mat4(1.0f);
            model = glm::translate(model, obj.position);
            if (obj.type == CUBE || obj.type == POINT_LIGHT || obj.type == DIRECTIONAL_LIGHT || obj.type == AMBIENT_LIGHT) {
                model = glm::rotate(model, glm::radians(obj.rotation.x), glm::vec3(1.0f, 0.0f, 0.0f));
                model = glm::rotate(model, glm::radians(obj.rotation.y), glm::vec3(0.0f, 1.0f, 0.0f));
                float scale = obj.scale;
                if (obj.type == POINT_LIGHT) {
                    scale = 0.15f;
                } else if (obj.type == DIRECTIONAL_LIGHT) {
                    scale = 0.2f;
                    glm::vec3 dir = glm::normalize(obj.lightDirection);
                    glm::vec3 up = glm::vec3(0.0f, 1.0f, 0.0f);
                    if (glm::abs(glm::dot(dir, up)) > 0.99f) up = glm::vec3(0.0f, 0.0f, 1.0f);
                    glm::vec3 right = glm::normalize(glm::cross(up, dir));
                    up = glm::cross(dir, right);
                    glm::mat4 rotation = glm::mat4(
                        glm::vec4(right, 0.0f),
                        glm::vec4(up, 0.0f),
                        glm::vec4(dir, 0.0f),
                        glm::vec4(0.0f, 0.0f, 0.0f, 1.0f)
                    );
                    model = model * rotation;
                } else if (obj.type == AMBIENT_LIGHT) {
                    scale = (selectedObjectId == obj.id) ? 0.2f + 0.05f * sin(globalTime * 2.0f) : 0.2f;
                }
                model = glm::scale(model, glm::vec3(scale));
            }
            modelMatrices.push_back(model);
        }
    }
    if (!modelMatrices.empty()) {
        glDrawElementsInstanced(GL_TRIANGLES, indexCounts[lod], GL_UNSIGNED_INT, 0, modelMatrices.size());
    }
    glBindVertexArray(0);
}

// Draw gizmo (for cube and directional light)
void drawGizmo(const glm::vec3& position, ObjectType type) {
    printf("Drawing gizmo for object at position (%.2f, %.2f, %.2f), type: %d\n", 
           position.x, position.y, position.z, type);

    glUniform1i(uniforms.isOutline, 0);
    glUniform1f(uniforms.time, globalTime);
    glm::mat4 model = glm::mat4(1.0f);
    model = glm::translate(model, position);

    if (type == DIRECTIONAL_LIGHT) {
        SceneObject* obj = scene.getObject(selectedObjectId);
        if (obj) {
            glm::vec3 dir = glm::normalize(obj->lightDirection);
            glm::vec3 up = glm::vec3(0.0f, 1.0f, 0.0f);
            if (glm::abs(glm::dot(dir, up)) > 0.99f) up = glm::vec3(0.0f, 0.0f, 1.0f);
            glm::vec3 right = glm::normalize(glm::cross(up, dir));
            up = glm::cross(dir, right);
            glm::mat4 rotation = glm::mat4(
                glm::vec4(right, 0.0f),
                glm::vec4(up, 0.0f),
                glm::vec4(dir, 0.0f),
                glm::vec4(0.0f, 0.0f, 0.0f, 1.0f)
            );
            model = model * rotation;
            model = glm::scale(model, glm::vec3(0.5f));
        }
    } else {
        model = glm::scale(model, glm::vec3(0.5f));
    }

    // Обновляем матрицу вида в UBO
    glm::mat4 view = glm::lookAt(
        glm::vec3(camPosX, camPosY, camPosZ),
        glm::vec3(camPosX, camPosY, camPosZ) + cameraFront,
        glm::vec3(0.0f, 1.0f, 0.0f)
    );
    glBindBuffer(GL_UNIFORM_BUFFER, matrixUBO);
    glBufferSubData(GL_UNIFORM_BUFFER, sizeof(glm::mat4), sizeof(glm::mat4), glm::value_ptr(view));
    glBindBuffer(GL_UNIFORM_BUFFER, 0);

    glBindVertexArray(gizmoVAO);
    if (type == DIRECTIONAL_LIGHT) {
        glDrawElements(GL_LINES, 6, GL_UNSIGNED_INT, (void*)(6 * sizeof(unsigned int)));
    } else {
        glDrawElements(GL_LINES, 6, GL_UNSIGNED_INT, 0);
    }
    glBindVertexArray(0);
}

// Draw sphere (for point light radius)
void drawSphere(const glm::vec3& position, float radius) {
    printf("Drawing sphere for point light at position (%.2f, %.2f, %.2f), radius: %.2f\n", 
           position.x, position.y, position.z, radius);

    glUniform1i(uniforms.isOutline, 0);
    glUniform1f(uniforms.time, globalTime);
    glm::mat4 model = glm::mat4(1.0f);
    model = glm::translate(model, position);
    model = glm::scale(model, glm::vec3(radius));

    // Обновляем матрицу вида в UBO
    glm::mat4 view = glm::lookAt(
        glm::vec3(camPosX, camPosY, camPosZ),
        glm::vec3(camPosX, camPosY, camPosZ) + cameraFront,
        glm::vec3(0.0f, 1.0f, 0.0f)
    );
    glBindBuffer(GL_UNIFORM_BUFFER, matrixUBO);
    glBufferSubData(GL_UNIFORM_BUFFER, sizeof(glm::mat4), sizeof(glm::mat4), glm::value_ptr(view));
    glBindBuffer(GL_UNIFORM_BUFFER, 0);

    glBindVertexArray(sphereVAO);
    glDrawElements(GL_LINES, sphereIndexCount, GL_UNSIGNED_INT, 0);
    glBindVertexArray(0);
}

// Update camera direction
void updateCameraFront() {
    if (!cameraDirty) return;
    float yawRad = glm::radians(camYaw);
    float pitchRad = glm::radians(camPitch);
    cameraFront = glm::normalize(glm::vec3(
        sin(yawRad) * cos(pitchRad),
        sin(pitchRad),
        -cos(yawRad) * cos(pitchRad)
    ));
    cameraDirty = false;
}

int main() {
    if (!glfwInit()) {
        printf("GLFW initialization failed\n");
        return -1;
    }

    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

    GLFWmonitor* primaryMonitor = glfwGetPrimaryMonitor();
    const GLFWvidmode* mode = glfwGetVideoMode(primaryMonitor);
    windowWidth = mode->width;
    windowHeight = mode->height;

    GLFWwindow* window = glfwCreateWindow(windowWidth, windowHeight, "GameEngine", NULL, NULL);
    if (!window) {
        printf("GLFW window creation failed\n");
        glfwTerminate();
        return -1;
    }

    glfwMakeContextCurrent(window);
    glfwSetFramebufferSizeCallback(window, framebufferSizeCallback);
    glfwSetKeyCallback(window, keyCallback);
    glfwSetCursorPosCallback(window, mouseMoveCallback);
    glfwSetMouseButtonCallback(window, mouseButtonCallback);

    glewExperimental = GL_TRUE;
    if (glewInit() != GLEW_OK) {
        printf("GLEW initialization failed\n");
        glfwDestroyWindow(window);
        glfwTerminate();
        return -1;
    }

    IMGUI_CHECKVERSION();
    ImGui::CreateContext();
    ImGuiIO& io = ImGui::GetIO(); (void)io;
    ImGui::StyleColorsDark();
    ImGui_ImplGlfw_InitForOpenGL(window, true);
    ImGui_ImplOpenGL3_Init("#version 330");

    glEnable(GL_DEPTH_TEST);
    glDepthFunc(GL_LESS);

    shaderProgram = createShaderProgram("vertex.glsl", "fragment.glsl");
    if (!shaderProgram) {
        glfwDestroyWindow(window);
        glfwTerminate();
        return -1;
    }

    for (int i = 0; i < NUM_LODS; i++) {
        initCubeVBO(i);
    }
    initInstanceVBO();
    initGizmoVBO();
    initSphereVBO();
    initMatrixUBO();

    // Добавляем один куб в сцену по умолчанию
    scene.addObject("Cube_1", glm::vec3(0.0f, 0.0f, 0.0f), glm::vec2(0.0f), 1.0f);

    projection = glm::perspective(glm::radians(45.0f), (float)windowWidth / windowHeight, 0.1f, 100.0f);
    glBindBuffer(GL_UNIFORM_BUFFER, matrixUBO);
    glBufferSubData(GL_UNIFORM_BUFFER, 0, sizeof(glm::mat4), glm::value_ptr(projection));
    glBindBuffer(GL_UNIFORM_BUFFER, 0);

    GLuint texture = loadTexture("images.bmp");
    if (!texture) {
        printf("Failed to load texture\n");
        glfwDestroyWindow(window);
        glfwTerminate();
        return -1;
    }

    glUseProgram(shaderProgram);
    glUniform1i(uniforms.material_diffuse, 0);
    glUniform3f(uniforms.material_specular, 0.5f, 0.5f, 0.5f);
    glUniform1f(uniforms.material_shininess, 32.0f);

    double lastTime = glfwGetTime();
    while (!glfwWindowShouldClose(window)) {
        double currentTime = glfwGetTime();
        globalDeltaTime = currentTime - lastTime;
        globalTime = currentTime;
        lastTime = currentTime;

        updateCameraFront();
        glm::mat4 view = glm::lookAt(
            glm::vec3(camPosX, camPosY, camPosZ),
            glm::vec3(camPosX, camPosY, camPosZ) + cameraFront,
            glm::vec3(0.0f, 1.0f, 0.0f)
        );
        glBindBuffer(GL_UNIFORM_BUFFER, matrixUBO);
        glBufferSubData(GL_UNIFORM_BUFFER, sizeof(glm::mat4), sizeof(glm::mat4), glm::value_ptr(view));
        glBindBuffer(GL_UNIFORM_BUFFER, 0);

        glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glUseProgram(shaderProgram);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture);
        glUniform3f(uniforms.viewPos, camPosX, camPosY, camPosZ);

        glm::vec3 lightPosition(0.0f, 1.0f, 0.0f);
        glm::vec3 lightColor(1.0f, 1.0f, 1.0f);
        float lightAmbientStrength = 0.2f;
        glm::vec3 lightDirection(0.0f, -1.0f, 0.0f);
        int lightType = 0;

        bool lightFound = false;
        for (const auto& obj : scene.getObjects()) {
            if (obj.type == POINT_LIGHT) {
                lightPosition = obj.position;
                lightColor = obj.lightColor;
                lightAmbientStrength = 0.2f;
                lightType = 0;
                lightFound = true;
                break;
            }
            else if (obj.type == DIRECTIONAL_LIGHT) {
                lightDirection = obj.lightDirection;
                lightColor = obj.lightColor;
                lightAmbientStrength = 0.0f;
                lightType = 1;
                lightFound = true;
                break;
            }
            else if (obj.type == AMBIENT_LIGHT) {
                lightColor = obj.lightColor;
                lightAmbientStrength = obj.lightIntensity;
                lightType = 2;
                lightFound = true;
                break;
            }
        }

        if (!lightFound) {
            lightAmbientStrength = 0.2f;
            lightType = 2;
        }

        glUniform3f(uniforms.light_position, lightPosition.x, lightPosition.y, lightPosition.z);
        glUniform3f(uniforms.light_color, lightColor.x, lightColor.y, lightColor.z);
        glUniform1f(uniforms.light_ambientStrength, lightAmbientStrength);
        glUniform3f(uniforms.light_direction, lightDirection.x, lightDirection.y, lightDirection.z);
        glUniform1i(uniforms.light_type, lightType);

        for (int i = 0; i < NUM_LODS; i++) {
            drawObjects(i, false, false);
        }

        for (int i = 0; i < NUM_LODS; i++) {
            drawObjects(i, true, false);
        }

        for (int i = 0; i < NUM_LODS; i++) {
            drawObjects(i, false, true);
        }

        for (const auto& obj : scene.getObjects()) {
            if (obj.id == selectedObjectId) {
                if (obj.type == CUBE) {
                    drawGizmo(obj.position, CUBE);
                }
                else if (obj.type == POINT_LIGHT) {
                    drawSphere(obj.position, obj.lightIntensity);
                }
                else if (obj.type == DIRECTIONAL_LIGHT) {
                    drawGizmo(obj.position, DIRECTIONAL_LIGHT);
                }
            }
        }

        drawImGui();

        glfwSwapBuffers(window);
        glfwPollEvents();
    }

    ImGui_ImplOpenGL3_Shutdown();
    ImGui_ImplGlfw_Shutdown();
    ImGui::DestroyContext();

    glDeleteVertexArrays(NUM_LODS, VAOs);
    glDeleteBuffers(NUM_LODS, VBOs);
    glDeleteBuffers(NUM_LODS, EBOs);
    glDeleteBuffers(1, &instanceVBO);
    glDeleteVertexArrays(1, &gizmoVAO);
    glDeleteBuffers(1, &gizmoVBO);
    glDeleteBuffers(1, &gizmoEBO);
    glDeleteVertexArrays(1, &sphereVAO);
    glDeleteBuffers(1, &sphereVBO);
    glDeleteBuffers(1, &sphereEBO);
    glDeleteProgram(shaderProgram);
    glDeleteTextures(1, &texture);

    glfwDestroyWindow(window);
    glfwTerminate();
    return 0;
}

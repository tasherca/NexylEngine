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
float camPosX = 0.0f, camPosY = 0.0f, camPosZ = 10.0f;
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
} uniforms;

// Gizmo VAO/VBO
GLuint gizmoVAO, gizmoVBO, gizmoEBO;
unsigned int gizmoIndexCount;

// Global variable for projection matrix
glm::mat4 projection = glm::mat4(1.0f);
int windowWidth = 800, windowHeight = 600;

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
    int height = *(int*) &header[22];
    int imageSize = *(int*) &header[34];

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
GLuint createShaderProgram() {
    std::string vertexShaderCode = readShaderFile("vertex.glsl");
    std::string fragmentShaderCode = readShaderFile("fragment.glsl");

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
    // Vertex format: position (x, y, z), texcoord (u, v), normal (nx, ny, nz)
    
    // High LOD: Full cube with 24 vertices (4 per face, 6 faces)
    float vertices_high[] = {
        // Front face (z = 0.5)
        -0.5f, -0.5f,  0.5f,  0.0f, 0.0f,  0.0f,  0.0f,  1.0f, // Bottom-left
         0.5f, -0.5f,  0.5f,  1.0f, 0.0f,  0.0f,  0.0f,  1.0f, // Bottom-right
         0.5f,  0.5f,  0.5f,  1.0f, 1.0f,  0.0f,  0.0f,  1.0f, // Top-right
        -0.5f,  0.5f,  0.5f,  0.0f, 1.0f,  0.0f,  0.0f,  1.0f, // Top-left
        // Back face (z = -0.5)
        -0.5f, -0.5f, -0.5f,  1.0f, 0.0f,  0.0f,  0.0f, -1.0f, // Bottom-left
         0.5f, -0.5f, -0.5f,  0.0f, 0.0f,  0.0f,  0.0f, -1.0f, // Bottom-right
         0.5f,  0.5f, -0.5f,  0.0f, 1.0f,  0.0f,  0.0f, -1.0f, // Top-right
        -0.5f,  0.5f, -0.5f,  1.0f, 1.0f,  0.0f,  0.0f, -1.0f, // Top-left
        // Left face (x = -0.5)
        -0.5f, -0.5f, -0.5f,  0.0f, 0.0f, -1.0f,  0.0f,  0.0f, // Bottom-back
        -0.5f, -0.5f,  0.5f,  1.0f, 0.0f, -1.0f,  0.0f,  0.0f, // Bottom-front
        -0.5f,  0.5f,  0.5f,  1.0f, 1.0f, -1.0f,  0.0f,  0.0f, // Top-front
        -0.5f,  0.5f, -0.5f,  0.0f, 1.0f, -1.0f,  0.0f,  0.0f, // Top-back
        // Right face (x = 0.5)
         0.5f, -0.5f,  0.5f,  0.0f, 0.0f,  1.0f,  0.0f,  0.0f, // Bottom-front
         0.5f, -0.5f, -0.5f,  1.0f, 0.0f,  1.0f,  0.0f,  0.0f, // Bottom-back
         0.5f,  0.5f, -0.5f,  1.0f, 1.0f,  1.0f,  0.0f,  0.0f, // Top-back
         0.5f,  0.5f,  0.5f,  0.0f, 1.0f,  1.0f,  0.0f,  0.0f, // Top-front
        // Top face (y = 0.5)
        -0.5f,  0.5f,  0.5f,  0.0f, 1.0f,  0.0f,  1.0f,  0.0f, // Front-left
         0.5f,  0.5f,  0.5f,  1.0f, 1.0f,  0.0f,  1.0f,  0.0f, // Front-right
         0.5f,  0.5f, -0.5f,  1.0f, 0.0f,  0.0f,  1.0f,  0.0f, // Back-right
        -0.5f,  0.5f, -0.5f,  0.0f, 0.0f,  0.0f,  1.0f,  0.0f, // Back-left
        // Bottom face (y = -0.5)
        -0.5f, -0.5f, -0.5f,  0.0f, 1.0f,  0.0f, -1.0f,  0.0f, // Back-left
         0.5f, -0.5f, -0.5f,  1.0f, 1.0f,  0.0f, -1.0f,  0.0f, // Back-right
         0.5f, -0.5f,  0.5f,  1.0f, 0.0f,  0.0f, -1.0f,  0.0f, // Front-right
        -0.5f, -0.5f,  0.5f,  0.0f, 0.0f,  0.0f, -1.0f,  0.0f  // Front-left
    };

    unsigned int indices_high[] = {
        // Front face
        0,  1,  2,   2,  3,  0,
        // Back face
        4,  5,  6,   6,  7,  4,
        // Left face
        8,  9, 10,  10, 11,  8,
        // Right face
        12, 13, 14,  14, 15, 12,
        // Top face
        16, 17, 18,  18, 19, 16,
        // Bottom face
        20, 21, 22,  22, 23, 20
    };

    // Medium LOD: Same vertex count (24 vertices), but simplified texture coordinates
    float vertices_medium[] = {
        // Front face (z = 0.5)
        -0.5f, -0.5f,  0.5f,  0.0f, 0.0f,  0.0f,  0.0f,  1.0f, // Bottom-left
         0.5f, -0.5f,  0.5f,  1.0f, 0.0f,  0.0f,  0.0f,  1.0f, // Bottom-right
         0.5f,  0.5f,  0.5f,  1.0f, 1.0f,  0.0f,  0.0f,  1.0f, // Top-right
        -0.5f,  0.5f,  0.5f,  0.0f, 1.0f,  0.0f,  0.0f,  1.0f, // Top-left
        // Back face (z = -0.5)
        -0.5f, -0.5f, -0.5f,  1.0f, 0.0f,  0.0f,  0.0f, -1.0f, // Bottom-left
         0.5f, -0.5f, -0.5f,  0.0f, 0.0f,  0.0f,  0.0f, -1.0f, // Bottom-right
         0.5f,  0.5f, -0.5f,  0.0f, 1.0f,  0.0f,  0.0f, -1.0f, // Top-right
        -0.5f,  0.5f, -0.5f,  1.0f, 1.0f,  0.0f,  0.0f, -1.0f, // Top-left
        // Left face (x = -0.5)
        -0.5f, -0.5f, -0.5f,  0.0f, 0.0f, -1.0f,  0.0f,  0.0f, // Bottom-back
        -0.5f, -0.5f,  0.5f,  1.0f, 0.0f, -1.0f,  0.0f,  0.0f, // Bottom-front
        -0.5f,  0.5f,  0.5f,  1.0f, 1.0f, -1.0f,  0.0f,  0.0f, // Top-front
        -0.5f,  0.5f, -0.5f,  0.0f, 1.0f, -1.0f,  0.0f,  0.0f, // Top-back
        // Right face (x = 0.5)
         0.5f, -0.5f,  0.5f,  0.0f, 0.0f,  1.0f,  0.0f,  0.0f, // Bottom-front
         0.5f, -0.5f, -0.5f,  1.0f, 0.0f,  1.0f,  0.0f,  0.0f, // Bottom-back
         0.5f,  0.5f, -0.5f,  1.0f, 1.0f,  1.0f,  0.0f,  0.0f, // Top-back
         0.5f,  0.5f,  0.5f,  0.0f, 1.0f,  1.0f,  0.0f,  0.0f, // Top-front
        // Top face (y = 0.5)
        -0.5f,  0.5f,  0.5f,  0.0f, 1.0f,  0.0f,  1.0f,  0.0f, // Front-left
         0.5f,  0.5f,  0.5f,  1.0f, 1.0f,  0.0f,  1.0f,  0.0f, // Front-right
         0.5f,  0.5f, -0.5f,  1.0f, 0.0f,  0.0f,  1.0f,  0.0f, // Back-right
        -0.5f,  0.5f, -0.5f,  0.0f, 0.0f,  0.0f,  1.0f,  0.0f, // Back-left
        // Bottom face (y = -0.5)
        -0.5f, -0.5f, -0.5f,  0.0f, 1.0f,  0.0f, -1.0f,  0.0f, // Back-left
         0.5f, -0.5f, -0.5f,  1.0f, 1.0f,  0.0f, -1.0f,  0.0f, // Back-right
         0.5f, -0.5f,  0.5f,  1.0f, 0.0f,  0.0f, -1.0f,  0.0f, // Front-right
        -0.5f, -0.5f,  0.5f,  0.0f, 0.0f,  0.0f, -1.0f,  0.0f  // Front-left
    };

    unsigned int indices_medium[] = {
        // Front face
        0,  1,  2,   2,  3,  0,
        // Back face
        4,  5,  6,   6,  7,  4,
        // Left face
        8,  9, 10,  10, 11,  8,
        // Right face
        12, 13, 14,  14, 15, 12,
        // Top face
        16, 17, 18,  18, 19, 16,
        // Bottom face
        20, 21, 22,  22, 23, 20
    };

    // Low LOD: Minimal cube with 8 vertices (shared across faces)
    float vertices_low[] = {
        // Corners of the cube
        -0.5f, -0.5f, -0.5f,  0.0f, 0.0f,  0.0f,  0.0f, -1.0f, // 0: Bottom-back-left
         0.5f, -0.5f, -0.5f,  1.0f, 0.0f,  0.0f,  0.0f, -1.0f, // 1: Bottom-back-right
         0.5f, -0.5f,  0.5f,  1.0f, 1.0f,  0.0f,  0.0f,  1.0f, // 2: Bottom-front-right
        -0.5f, -0.5f,  0.5f,  0.0f, 1.0f,  0.0f,  0.0f,  1.0f, // 3: Bottom-front-left
        -0.5f,  0.5f, -0.5f,  0.0f, 0.0f,  0.0f,  0.0f, -1.0f, // 4: Top-back-left
         0.5f,  0.5f, -0.5f,  1.0f, 0.0f,  0.0f,  0.0f, -1.0f, // 5: Top-back-right
         0.5f,  0.5f,  0.5f,  1.0f, 1.0f,  0.0f,  0.0f,  1.0f, // 6: Top-front-right
        -0.5f,  0.5f,  0.5f,  0.0f, 1.0f,  0.0f,  0.0f,  1.0f  // 7: Top-front-left
    };

    unsigned int indices_low[] = {
        // Front face (z = 0.5)
        3, 2, 6,   6, 7, 3,
        // Back face (z = -0.5)
        0, 1, 5,   5, 4, 0,
        // Left face (x = -0.5)
        0, 4, 7,   7, 3, 0,
        // Right face (x = 0.5)
        1, 2, 6,   6, 5, 1,
        // Top face (y = 0.5)
        4, 5, 6,   6, 7, 4,
        // Bottom face (y = -0.5)
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

    // Vertex attributes: position, texcoord, normal
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
void updateInstanceVBO(int lod) {
    std::vector<glm::mat4> modelMatrices;
    std::vector<float> selections;
    glm::vec3 camPos(camPosX, camPosY, camPosZ);

    for (const auto& obj : scene.getObjects()) {
        if (obj.type != CUBE) continue;
        float distance = glm::length(obj.position - camPos);
        int selectedLOD = selectLOD(distance);
        if (selectedLOD != lod) continue; // Only include objects for this LOD

        glm::mat4 model = glm::mat4(1.0f);
        model = glm::translate(model, obj.position);
        model = glm::rotate(model, glm::radians(obj.rotation.x), glm::vec3(1.0f, 0.0f, 0.0f));
        model = glm::rotate(model, glm::radians(obj.rotation.y), glm::vec3(0.0f, 1.0f, 0.0f));
        model = glm::scale(model, glm::vec3(obj.scale));
        modelMatrices.push_back(model);
        selections.push_back(obj.id == selectedObjectId ? 1.0f : 0.0f);
    }

    // Only update VBO if there are objects to render
    if (!modelMatrices.empty()) {
        glBindBuffer(GL_ARRAY_BUFFER, instanceVBO);
        glBufferData(GL_ARRAY_BUFFER, modelMatrices.size() * (sizeof(glm::mat4) + sizeof(float)), nullptr, GL_DYNAMIC_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0, modelMatrices.size() * sizeof(glm::mat4), modelMatrices.data());
        glBufferSubData(GL_ARRAY_BUFFER, modelMatrices.size() * sizeof(glm::mat4), selections.size() * sizeof(float), selections.data());

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
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
    }
}

// Initialize gizmo VBO/VAO
void initGizmoVBO() {
    float gizmoVertices[] = {
        0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f,
        1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f,
        0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f,
        0.0f, 0.707f, 0.707f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f,
        0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f,
        0.0f, 0.707f, -0.707f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f,
        0.0f, 0.0f, -1.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f,
        0.0f, -0.707f, -0.707f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f,
        0.0f, -1.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f,
        0.0f, -0.707f, 0.707f, 1.0f, 0.0f, 0.0f, 0.0f, 1.0f,
        1.0f, -0.1f, -0.1f, 1.0f, 0.0f, 0.0f, 0.0f, 2.0f,
        1.0f, 0.1f, -0.1f, 1.0f, 0.0f, 0.0f, 0.0f, 2.0f,
        1.0f, 0.1f, 0.1f, 1.0f, 0.0f, 0.0f, 0.0f, 2.0f,
        1.0f, -0.1f, 0.1f, 1.0f, 0.0f, 0.0f, 0.0f, 2.0f
    };

    unsigned int gizmoIndices[] = {
        0, 1, 2, 3, 4, 5,
        6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13, 6,
        14, 15, 16, 16, 17, 14
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

    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 8 * sizeof(float), (void*)0);
    glEnableVertexAttribArray(0);
    glVertexAttribPointer(2, 3, GL_FLOAT, GL_FALSE, 8 * sizeof(float), (void*)(5 * sizeof(float)));
    glEnableVertexAttribArray(2);
    glVertexAttribPointer(3, 1, GL_FLOAT, GL_FALSE, 8 * sizeof(float), (void*)(7 * sizeof(float)));
    glEnableVertexAttribArray(3);

    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindVertexArray(0);
}

// Select LOD based on distance
int selectLOD(float distance) {
    // Clear distance thresholds: high LOD (<5 units), medium LOD (5-15 units), low LOD (>15 units)
    if (distance < 5.0f) return 0; // High LOD
    if (distance < 15.0f) return 1; // Medium LOD
    return 2; // Low LOD
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
            // Check if mouse is over ImGui window
            bool isOverImGui = ImGui::GetIO().WantCaptureMouse;

            isDragging = true;
            double xpos, ypos;
            glfwGetCursorPos(window, &xpos, &ypos);
            int width, height;
            glfwGetWindowSize(window, &width, &height);
            lastX = (xpos / width) * 2 - 1;
            lastY = -((ypos / height) * 2 - 1);

            // Only handle deselection if not over ImGui and in default mode
            if (!isOverImGui && !isRotating && !isScaling && !isTranslating) {
                // Check if we clicked on empty space to deselect
                bool clickedOnObject = false;
                for (const auto& obj : scene.getObjects()) {
                    if (obj.type == CUBE) {
                        // Simple distance check (could be improved with proper raycasting)
                        glm::vec3 screenPos = glm::project(
                            obj.position,
                            glm::lookAt(glm::vec3(camPosX, camPosY, camPosZ),
                                glm::vec3(camPosX, camPosY, camPosZ) + cameraFront,
                                glm::vec3(0.0f, 1.0f, 0.0f)),
                            projection,
                            glm::vec4(0, 0, width, height)
                        );
                        float dist = glm::distance(glm::vec2(screenPos), glm::vec2(xpos, height - ypos));
                        if (dist < 30.0f) { // Threshold distance
                            clickedOnObject = true;
                            selectedObjectId = obj.id; // Select the clicked object
                            break;
                        }
                    }
                }

                if (!clickedOnObject) {
                    selectedObjectId = -1; // Deselect if clicked on empty space
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

// Draw text (stub)
void drawText(float /*x*/, float /*y*/, const char* /*text*/, float /*r*/, float /*g*/, float /*b*/) {
    // Text rendering implementation
}

// Draw UI
void drawUI(int /*lod*/) {
    // UI rendering implementation
}

// Draw ImGui panel
void drawImGui() {
    // Start new ImGui frame
    ImGui_ImplOpenGL3_NewFrame();
    ImGui_ImplGlfw_NewFrame();
    ImGui::NewFrame();

    // Scene hierarchy window (fixed to the left)
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
                if (ImGui::DragFloat("Intensity", &intensity, 0.01f, 0.0f, 10.0f)) {
                    scene.getObject(obj.id)->lightIntensity = intensity;
                    sceneDirty = true;
                }
                if (obj.type == DIRECTIONAL_LIGHT) {
                    float dir[3] = { obj.lightDirection.x, obj.lightDirection.y, obj.lightDirection.z };
                    if (ImGui::DragFloat3("Direction", dir, 0.1f)) {
                        scene.getObject(obj.id)->lightDirection = glm::normalize(glm::vec3(dir[0], dir[1], dir[2]));
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

// Draw cubes with instancing
void drawCubes(int lod, bool isOutlinePass) {
    glUniform1i(uniforms.isOutline, isOutlinePass ? 1 : 0);
    glUniform1f(uniforms.outlineWidth, 0.2f); // Adjust outline width as needed
    updateInstanceVBO(lod);
    glBindVertexArray(VAOs[lod]);
    // Only draw if there are instances to avoid OpenGL errors
    std::vector<glm::mat4> modelMatrices;
    glm::vec3 camPos(camPosX, camPosY, camPosZ);
    for (const auto& obj : scene.getObjects()) {
        if (obj.type != CUBE) continue;
        float distance = glm::length(obj.position - camPos);
        if (selectLOD(distance) == lod) {
            glm::mat4 model = glm::mat4(1.0f);
            model = glm::translate(model, obj.position);
            model = glm::rotate(model, glm::radians(obj.rotation.x), glm::vec3(1.0f, 0.0f, 0.0f));
            model = glm::rotate(model, glm::radians(obj.rotation.y), glm::vec3(0.0f, 1.0f, 0.0f));
            model = glm::scale(model, glm::vec3(obj.scale));
            modelMatrices.push_back(model);
        }
    }
    if (!modelMatrices.empty()) {
        glDrawElementsInstanced(GL_TRIANGLES, indexCounts[lod], GL_UNSIGNED_INT, 0, modelMatrices.size());
    }
    glBindVertexArray(0);
}

// Draw gizmo
void drawGizmo(const glm::vec3& position) {
    glUniform1i(uniforms.isOutline, 0);
    glm::mat4 model = glm::mat4(1.0f);
    model = glm::translate(model, position);

    glBindBuffer(GL_UNIFORM_BUFFER, matrixUBO);
    glBufferSubData(GL_UNIFORM_BUFFER, sizeof(glm::mat4), sizeof(glm::mat4), glm::value_ptr(model));
    glBindBuffer(GL_UNIFORM_BUFFER, 0);

    glBindVertexArray(gizmoVAO);
    glDrawElements(GL_LINES, gizmoIndexCount, GL_UNSIGNED_INT, 0);
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
    glfwSwapInterval(1);

    glfwSetFramebufferSizeCallback(window, framebufferSizeCallback);
    glfwSetKeyCallback(window, keyCallback);
    glfwSetCursorPosCallback(window, mouseMoveCallback);
    glfwSetMouseButtonCallback(window, mouseButtonCallback);

    glewExperimental = GL_TRUE;
    if (glewInit() != GLEW_OK) {
        printf("GLEW initialization failed!\n");
        glfwTerminate();
        return -1;
    }

    IMGUI_CHECKVERSION();
    ImGui::CreateContext();
    ImGuiIO& io = ImGui::GetIO(); (void)io;
    ImGui::StyleColorsDark();
    ImGui_ImplGlfw_InitForOpenGL(window, true);
    ImGui_ImplOpenGL3_Init("#version 330");

    // Load texture and verify
    GLuint diffuseTexture = loadTexture("images.bmp");
    if (diffuseTexture == 0) {
        printf("Texture loading failed\n");
        glfwTerminate();
        return -1;
    }

    shaderProgram = createShaderProgram();
    if (shaderProgram == 0) {
        printf("Shader program creation failed\n");
        glDeleteTextures(1, &diffuseTexture);
        glfwTerminate();
        return -1;
    }

    initMatrixUBO();
    GLuint blockIndex = glGetUniformBlockIndex(shaderProgram, "Matrices");
    if (blockIndex != GL_INVALID_INDEX) {
        glUniformBlockBinding(shaderProgram, blockIndex, 0);
    }

    glUseProgram(shaderProgram);
    glUniform1i(uniforms.material_diffuse, 0); // Bind texture to unit 0
    glUniform3fv(uniforms.material_specular, 1, glm::value_ptr(glm::vec3(1.0f)));
    glUniform1f(uniforms.material_shininess, 32.0f);
    glUniform3fv(uniforms.light_position, 1, glm::value_ptr(glm::vec3(10.0f, 10.0f, 10.0f)));
    glUniform3fv(uniforms.light_color, 1, glm::value_ptr(glm::vec3(1.0f)));
    glUniform1f(uniforms.light_ambientStrength, 0.1f);
    glUniform3fv(uniforms.light_direction, 1, glm::value_ptr(glm::vec3(0.0f, -1.0f, 0.0f)));
    glUniform1i(uniforms.light_type, 0);

    for (int i = 0; i < NUM_LODS; i++) {
        initCubeVBO(i);
    }
    initInstanceVBO();
    initGizmoVBO();

    glEnable(GL_DEPTH_TEST);
    glEnable(GL_STENCIL_TEST);
    glEnable(GL_CULL_FACE);
    glCullFace(GL_BACK); // Cull back faces
    glFrontFace(GL_CCW); // Counterclockwise winding for front faces

    projection = glm::perspective(glm::radians(45.0f), (float)windowWidth / windowHeight, 0.1f, 100.0f);
    glBindBuffer(GL_UNIFORM_BUFFER, matrixUBO);
    glBufferData(GL_UNIFORM_BUFFER, 2 * sizeof(glm::mat4), NULL, GL_DYNAMIC_DRAW);
    glBufferSubData(GL_UNIFORM_BUFFER, 0, sizeof(glm::mat4), glm::value_ptr(projection));
    glBindBuffer(GL_UNIFORM_BUFFER, 0);

    scene.addObject("Cube_1", glm::vec3(0.0f), glm::vec2(0.0f), 0.5f);
    sceneDirty = true;

    double lastTime = glfwGetTime();
    while (!glfwWindowShouldClose(window)) {
        double currentTime = glfwGetTime();
        globalDeltaTime = std::min(currentTime - lastTime, 0.1);
        lastTime = currentTime;

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT | GL_STENCIL_BUFFER_BIT);

        glUseProgram(shaderProgram);

        // Bind texture
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, diffuseTexture);

        updateCameraFront();

        glm::mat4 view = glm::lookAt(
            glm::vec3(camPosX, camPosY, camPosZ),
            glm::vec3(camPosX, camPosY, camPosZ) + cameraFront,
            glm::vec3(0.0f, 1.0f, 0.0f)
        );

        glBindBuffer(GL_UNIFORM_BUFFER, matrixUBO);
        glBufferSubData(GL_UNIFORM_BUFFER, sizeof(glm::mat4), sizeof(glm::mat4), glm::value_ptr(view));
        glBindBuffer(GL_UNIFORM_BUFFER, 0);

        glUniform3fv(uniforms.viewPos, 1, glm::value_ptr(glm::vec3(camPosX, camPosY, camPosZ)));

        bool lightFound = false;
        for (const auto& obj : scene.getObjects()) {
            if (obj.type == AMBIENT_LIGHT) {
                glUniform1f(uniforms.light_ambientStrength, obj.lightIntensity);
                glUniform3fv(uniforms.light_color, 1, glm::value_ptr(obj.lightColor));
                glUniform1i(uniforms.light_type, 2);
                lightFound = true;
                break;
            }
            else if (obj.type == POINT_LIGHT) {
                glUniform3fv(uniforms.light_position, 1, glm::value_ptr(obj.position));
                glUniform3fv(uniforms.light_color, 1, glm::value_ptr(obj.lightColor));
                glUniform1f(uniforms.light_ambientStrength, 0.1f);
                glUniform1i(uniforms.light_type, 0);
                lightFound = true;
                break;
            }
            else if (obj.type == DIRECTIONAL_LIGHT) {
                glUniform3fv(uniforms.light_direction, 1, glm::value_ptr(obj.lightDirection));
                glUniform3fv(uniforms.light_color, 1, glm::value_ptr(obj.lightColor));
                glUniform1f(uniforms.light_ambientStrength, 0.1f);
                glUniform1i(uniforms.light_type, 1);
                lightFound = true;
                break;
            }
        }
        if (!lightFound) {
            glUniform3fv(uniforms.light_position, 1, glm::value_ptr(glm::vec3(10.0f, 10.0f, 10.0f)));
            glUniform3fv(uniforms.light_color, 1, glm::value_ptr(glm::vec3(1.0f)));
            glUniform1f(uniforms.light_ambientStrength, 0.1f);
            glUniform1i(uniforms.light_type, 0);
        }

        // Draw cubes with instancing for each LOD
        for (int lod = 0; lod < NUM_LODS; lod++) {
            if (selectedObjectId != -1) {
                glStencilFunc(GL_ALWAYS, 1, 0xFF);
                glStencilOp(GL_KEEP, GL_KEEP, GL_REPLACE);
                glStencilMask(0xFF);

                drawCubes(lod, false);

                glStencilFunc(GL_NOTEQUAL, 1, 0xFF);
                glStencilMask(0x00);
                glDisable(GL_DEPTH_TEST);

                drawCubes(lod, true);

                glStencilMask(0xFF);
                glStencilFunc(GL_ALWAYS, 0, 0xFF);
                glEnable(GL_DEPTH_TEST);
            }
            else {
                drawCubes(lod, false);
            }
        }

        // Draw gizmo
        if (selectedObjectId != -1 && (isTranslating || isRotating || isScaling)) {
            SceneObject* obj = scene.getObject(selectedObjectId);
            if (obj && obj->type == CUBE) {
                drawGizmo(obj->position);
            }
        }

        // Disable depth and stencil testing for ImGui
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_STENCIL_TEST);

        drawUI(0);
        drawImGui();

        // Re-enable depth and stencil testing
        glEnable(GL_DEPTH_TEST);
        glEnable(GL_STENCIL_TEST);

        sceneDirty = false;

        glfwSwapBuffers(window);
        glfwPollEvents();
    }

    ImGui_ImplOpenGL3_Shutdown();
    ImGui_ImplGlfw_Shutdown();
    ImGui::DestroyContext();

    glDeleteTextures(1, &diffuseTexture);
    glDeleteVertexArrays(NUM_LODS, VAOs);
    glDeleteBuffers(NUM_LODS, VBOs);
    glDeleteBuffers(NUM_LODS, EBOs);
    glDeleteBuffers(1, &instanceVBO);
    glDeleteBuffers(1, &matrixUBO);
    glDeleteVertexArrays(1, &gizmoVAO);
    glDeleteBuffers(1, &gizmoVBO);
    glDeleteBuffers(1, &gizmoEBO);
    glDeleteProgram(shaderProgram);

    glfwTerminate();
    return 0;
}

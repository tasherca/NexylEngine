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

// Глобальные переменные для трансформаций
float angleX = 0.0f, angleY = 0.0f;
float scale = 0.5f;
float posX = 0.0f, posY = 0.0f, posZ = 0.0f;
float lastX = 0.0f, lastY = 0.0f;
bool isRotating = false;
bool isScaling = false;
bool isTranslating = false;
bool isDragging = false;

// Глобальные переменные для камеры
float camPosX = 0.0f, camPosY = 0.0f, camPosZ = 10.0f;
float camYaw = 0.0f, camPitch = 0.0f;
float camSpeed = 5.0f;
float camRotSpeed = 0.1f;
float lastMouseX = 400.0f, lastMouseY = 300.0f;
bool firstMouse = true;

// Глобальные переменные для VBO, VAO и EBO
#define NUM_LODS 3
GLuint VAOs[NUM_LODS], VBOs[NUM_LODS], EBOs[NUM_LODS];
unsigned int indexCounts[NUM_LODS];
GLuint shaderProgram;

// Глобальная переменная для хранения матрицы проекции
glm::mat4 projection = glm::mat4(1.0f);
int windowWidth = 800, windowHeight = 600;

// Функция чтения шейдера из файла
std::string readShaderFile(const char* filename) {
    std::ifstream file(filename);
    if (!file.is_open()) {
        printf("Не удалось открыть файл шейдера: %s\n", filename);
        return "";
    }
    std::stringstream buffer;
    buffer << file.rdbuf();
    return buffer.str();
}

// Функция загрузки текстуры BMP
GLuint loadTexture(const char* filename) {
    FILE* file = fopen(filename, "rb");
    if (!file) {
        printf("Не удалось открыть файл текстуры!\n");
        return 0;
    }

    unsigned char header[54];
    fread(header, 1, 54, file);

    int width = *(int*)&header[18];
    int height = *(int*)&header[22];
    int imageSize = *(int*)&header[34];

    unsigned char* data = new unsigned char[imageSize];
    fread(data, 1, imageSize, file);
    fclose(file);

    GLuint textureID;
    glGenTextures(1, &textureID);
    glBindTexture(GL_TEXTURE_2D, textureID);

    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, GL_BGR, GL_UNSIGNED_BYTE, data);

    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

    delete[] data;
    return textureID;
}

// Функция создания шейдерной программы
GLuint createShaderProgram() {
    // Чтение шейдеров из файлов
    std::string vertexShaderCode = readShaderFile("vertex.glsl");
    std::string fragmentShaderCode = readShaderFile("fragment.glsl");
    
    if (vertexShaderCode.empty() || fragmentShaderCode.empty()) {
        printf("Ошибка чтения файлов шейдеров\n");
        return 0;
    }

    const char* vertexShaderSource = vertexShaderCode.c_str();
    const char* fragmentShaderSource = fragmentShaderCode.c_str();

    GLuint vertexShader = glCreateShader(GL_VERTEX_SHADER);
    glShaderSource(vertexShader, 1, &vertexShaderSource, NULL);
    glCompileShader(vertexShader);

    GLint success;
    glGetShaderiv(vertexShader, GL_COMPILE_STATUS, &success);
    if (!success) {
        char infoLog[512];
        glGetShaderInfoLog(vertexShader, 512, NULL, infoLog);
        printf("Ошибка компиляции вершинного шейдера: %s\n", infoLog);
    }

    GLuint fragmentShader = glCreateShader(GL_FRAGMENT_SHADER);
    glShaderSource(fragmentShader, 1, &fragmentShaderSource, NULL);
    glCompileShader(fragmentShader);

    glGetShaderiv(fragmentShader, GL_COMPILE_STATUS, &success);
    if (!success) {
        char infoLog[512];
        glGetShaderInfoLog(fragmentShader, 512, NULL, infoLog);
        printf("Ошибка компиляции фрагментного шейдера: %s\n", infoLog);
    }

    GLuint shaderProgram = glCreateProgram();
    glAttachShader(shaderProgram, vertexShader);
    glAttachShader(shaderProgram, fragmentShader);
    glLinkProgram(shaderProgram);

    glGetProgramiv(shaderProgram, GL_LINK_STATUS, &success);
    if (!success) {
        char infoLog[512];
        glGetProgramInfoLog(shaderProgram, 512, NULL, infoLog);
        printf("Ошибка линковки программы шейдеров: %s\n", infoLog);
    }

    glDeleteShader(vertexShader);
    glDeleteShader(fragmentShader);
    return shaderProgram;
}

// Инициализация VBO и VAO для LOD
void initCubeVBO(int lod) {
    float vertices_high[] = {
        -0.5f, -0.5f,  0.5f,  0.0f, 0.0f,  0.0f, 0.0f, 1.0f,
         0.5f, -0.5f,  0.5f,  1.0f, 0.0f,  0.0f, 0.0f, 1.0f,
         0.5f,  0.5f,  0.5f,  1.0f, 1.0f,  0.0f, 0.0f, 1.0f,
        -0.5f,  0.5f,  0.5f,  0.0f, 1.0f,  0.0f, 0.0f, 1.0f,
        -0.5f, -0.5f, -0.5f,  1.0f, 0.0f,  0.0f, 0.0f, -1.0f,
        -0.5f,  0.5f, -0.5f,  1.0f, 1.0f,  0.0f, 0.0f, -1.0f,
         0.5f,  0.5f, -0.5f,  0.0f, 1.0f,  0.0f, 0.0f, -1.0f,
         0.5f, -0.5f, -0.5f,  0.0f, 0.0f,  0.0f, 0.0f, -1.0f,
        -0.5f,  0.5f, -0.5f,  0.0f, 1.0f,  0.0f, 1.0f, 0.0f,
        -0.5f,  0.5f,  0.5f,  0.0f, 0.0f,  0.0f, 1.0f, 0.0f,
         0.5f,  0.5f,  0.5f,  1.0f, 0.0f,  0.0f, 1.0f, 0.0f,
         0.5f,  0.5f, -0.5f,  1.0f, 1.0f,  0.0f, 1.0f, 0.0f,
        -0.5f, -0.5f, -0.5f,  1.0f, 1.0f,  0.0f, -1.0f, 0.0f,
         0.5f, -0.5f, -0.5f,  0.0f, 1.0f,  0.0f, -1.0f, 0.0f,
         0.5f, -0.5f,  0.5f,  0.0f, 0.0f,  0.0f, -1.0f, 0.0f,
        -0.5f, -0.5f,  0.5f,  1.0f, 0.0f,  0.0f, -1.0f, 0.0f,
         0.5f, -0.5f, -0.5f,  1.0f, 0.0f,  1.0f, 0.0f, 0.0f,
         0.5f,  0.5f, -0.5f,  1.0f, 1.0f,  1.0f, 0.0f, 0.0f,
         0.5f,  0.5f,  0.5f,  0.0f, 1.0f,  1.0f, 0.0f, 0.0f,
         0.5f, -0.5f,  0.5f,  0.0f, 0.0f,  1.0f, 0.0f, 0.0f,
        -0.5f, -0.5f, -0.5f,  0.0f, 0.0f,  -1.0f, 0.0f, 0.0f,
        -0.5f, -0.5f,  0.5f,  1.0f, 0.0f,  -1.0f, 0.0f, 0.0f,
        -0.5f,  0.5f,  0.5f,  1.0f, 1.0f,  -1.0f, 0.0f, 0.0f,
        -0.5f,  0.5f, -0.5f,  0.0f, 1.0f,  -1.0f, 0.0f, 0.0f
    };

    unsigned int indices_high[] = {
        0, 1, 2, 2, 3, 0,
        4, 5, 6, 6, 7, 4,
        8, 9, 10, 10, 11, 8,
        12, 13, 14, 14, 15, 12,
        16, 17, 18, 18, 19, 16,
        20, 21, 22, 22, 23, 20
    };

    float vertices_medium[] = {
        -0.5f, -0.5f,  0.5f,  0.0f, 0.0f,  0.0f, 0.0f, 1.0f,
         0.5f, -0.5f,  0.5f,  1.0f, 0.0f,  0.0f, 0.0f, 1.0f,
         0.5f,  0.5f,  0.5f,  1.0f, 1.0f,  0.0f, 0.0f, 1.0f,
        -0.5f,  0.5f,  0.5f,  0.0f, 1.0f,  0.0f, 0.0f, 1.0f,
        -0.5f, -0.5f, -0.5f,  1.0f, 0.0f,  0.0f, 0.0f, -1.0f,
         0.5f, -0.5f, -0.5f,  0.0f, 0.0f,  0.0f, 0.0f, -1.0f,
         0.5f,  0.5f, -0.5f,  0.0f, 1.0f,  0.0f, 0.0f, -1.0f,
        -0.5f,  0.5f, -0.5f,  1.0f, 1.0f,  0.0f, 0.0f, -1.0f
    };

    unsigned int indices_medium[] = {
        0, 1, 2, 2, 3, 0,
        4, 5, 6, 6, 7, 4
    };

    float vertices_low[] = {
        -0.5f, -0.5f,  0.5f,  0.0f, 0.0f,  0.0f, 0.0f, 1.0f,
         0.5f, -0.5f,  0.5f,  1.0f, 0.0f,  0.0f, 0.0f, 1.0f,
         0.5f,  0.5f,  0.5f,  1.0f, 1.0f,  0.0f, 0.0f, 1.0f,
        -0.5f,  0.5f,  0.5f,  0.0f, 1.0f,  0.0f, 0.0f, 1.0f
    };

    unsigned int indices_low[] = {
        0, 1, 2, 2, 3, 0
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

    // Позиция вершин
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE, 8 * sizeof(float), (void*)0);
    glEnableVertexAttribArray(0);
    // Текстурные координаты
    glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 8 * sizeof(float), (void*)(3 * sizeof(float)));
    glEnableVertexAttribArray(1);
    // Нормали
    glVertexAttribPointer(2, 3, GL_FLOAT, GL_FALSE, 8 * sizeof(float), (void*)(5 * sizeof(float)));
    glEnableVertexAttribArray(2);

    glBindBuffer(GL_ARRAY_BUFFER, 0);
    glBindVertexArray(0);
}

// Выбор LOD на основе расстояния
int selectLOD(float distance) {
    if (distance < 5.0f) return 0;
    if (distance < 10.0f) return 1;
    return 2;
}

// Callback для изменения размера окна
void framebufferSizeCallback(GLFWwindow* window, int width, int height) {
    glViewport(0, 0, width, height);
    windowWidth = width;
    windowHeight = height;
    projection = glm::perspective(glm::radians(45.0f), (float)width / height, 0.1f, 100.0f);
}

// Глобальная переменная для передачи deltaTime в keyCallback
float globalDeltaTime = 0.016f;

// Обработка клавиш
void keyCallback(GLFWwindow* window, int key, int scancode, int action, int mods) {
    if (mods == GLFW_MOD_CONTROL && action == GLFW_PRESS) {
        isRotating = isScaling = isTranslating = false;
        if (key == GLFW_KEY_R) {
            isRotating = true;
            printf("Режим вращения\n");
        } else if (key == GLFW_KEY_S) {
            isScaling = true;
            printf("Режим масштабирования\n");
        } else if (key == GLFW_KEY_T) {
            isTranslating = true;
            printf("Режим перемещения\n");
        }
    }

    float deltaTime = globalDeltaTime;
    float yawRad = glm::radians(camYaw);
    float pitchRad = glm::radians(camPitch);
    glm::vec3 front(
        sin(yawRad) * cos(pitchRad),
        sin(pitchRad),
        -cos(yawRad) * cos(pitchRad)
    );
    front = glm::normalize(front);
    glm::vec3 right = glm::normalize(glm::cross(front, glm::vec3(0.0f, 1.0f, 0.0f)));

    if (action == GLFW_PRESS || action == GLFW_REPEAT) {
        if (key == GLFW_KEY_W) {
            camPosX += camSpeed * deltaTime * front.x;
            camPosY += camSpeed * deltaTime * front.y;
            camPosZ += camSpeed * deltaTime * front.z;
        } else if (key == GLFW_KEY_S) {
            camPosX -= camSpeed * deltaTime * front.x;
            camPosY -= camSpeed * deltaTime * front.y;
            camPosZ -= camSpeed * deltaTime * front.z;
        } else if (key == GLFW_KEY_A) {
            camPosX -= camSpeed * deltaTime * right.x;
            camPosZ -= camSpeed * deltaTime * right.z;
        } else if (key == GLFW_KEY_D) {
            camPosX += camSpeed * deltaTime * right.x;
            camPosZ += camSpeed * deltaTime * right.z;
        } else if (key == GLFW_KEY_SPACE) {
            camPosY += camSpeed * deltaTime;
        } else if (key == GLFW_KEY_LEFT_CONTROL) {
            camPosY -= camSpeed * deltaTime;
        }
    }
}

// Обработка движения мыши для камеры
void mouseMoveCallback(GLFWwindow* window, double xpos, double ypos) {
    if (isDragging) {
        int width, height;
        glfwGetWindowSize(window, &width, &height);
        float x = (xpos / width) * 2 - 1;
        float y = -((ypos / height) * 2 - 1);

        if (isRotating) {
            angleX += (y - lastY) * 180.0f;
            angleY += (x - lastX) * 180.0f;
        } else if (isScaling) {
            scale += (y - lastY) * 2.0f;
            if (scale < 0.1f) scale = 0.1f;
            if (scale > 2.0f) scale = 2.0f;
        } else if (isTranslating) {
            posX += (x - lastX);
            posY += (y - lastY);
        }
        lastX = x;
        lastY = y;
    } else {
        if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS) {
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
        } else {
            firstMouse = true;
        }
    }
}

// Обработка нажатий кнопок мыши
void mouseButtonCallback(GLFWwindow* window, int button, int action, int mods) {
    if (button == GLFW_MOUSE_BUTTON_LEFT) {
        if (action == GLFW_PRESS) {
            isDragging = true;
            double xpos, ypos;
            glfwGetCursorPos(window, &xpos, &ypos);
            int width, height;
            glfwGetWindowSize(window, &width, &height);
            lastX = (xpos / width) * 2 - 1;
            lastY = -((ypos / height) * 2 - 1);
        } else if (action == GLFW_RELEASE) {
            isDragging = false;
        }
    }
}

// Отрисовка текста (заглушка)
void drawText(float x, float y, const char* text, float r, float g, float b) {
    printf("Text at (%.1f, %.1f): %s\n", x, y, text);
}

// Отрисовка UI
void drawUI(int lod) {
    drawText(10, 20, "Управление: Ctrl+R - вращение, Ctrl+S - масштаб, Ctrl+T - перемещение", 1.0f, 1.0f, 1.0f);
    drawText(10, 40, "Текущий режим: ", 1.0f, 1.0f, 1.0f);
    if (isRotating) {
        drawText(120, 40, "ВРАЩЕНИЕ", 1.0f, 0.0f, 0.0f);
    } else if (isScaling) {
        drawText(120, 40, "МАСШТАБИРОВАНИЕ", 0.0f, 1.0f, 0.0f);
    } else if (isTranslating) {
        drawText(120, 40, "ПЕРЕМЕЩЕНИЕ", 0.0f, 0.0f, 1.0f);
    }
    char lodText[50];
    sprintf(lodText, "Текущий LOD: %d", lod);
    drawText(10, 60, lodText, 1.0f, 1.0f, 0.0f);
    drawText(10, 80, "Камера: WASD - движение, Space/Ctrl - вверх/вниз, Правая кнопка мыши - поворот", 1.0f, 1.0f, 1.0f);
}

// Отрисовка куба
void drawCube(int lod) {
    glBindVertexArray(VAOs[lod]);
    glDrawElements(GL_TRIANGLES, indexCounts[lod], GL_UNSIGNED_INT, 0);
    glBindVertexArray(0);
}

int main() {
    if (!glfwInit()) {
        return -1;
    }

    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);

    GLFWwindow* window = glfwCreateWindow(windowWidth, windowHeight, "OpenGL 3D Transformations with LOD and Lighting", NULL, NULL);
    if (!window) {
        glfwTerminate();
        return -1;
    }

    glfwMakeContextCurrent(window);

    // Включение V-Sync
    glfwSwapInterval(1);

    // Установка callback для изменения размера окна
    glfwSetFramebufferSizeCallback(window, framebufferSizeCallback);

    glewExperimental = GL_TRUE;
    if (glewInit() != GLEW_OK) {
        printf("Ошибка инициализации GLEW!\n");
        glfwTerminate();
        return -1;
    }

    glfwSetKeyCallback(window, keyCallback);
    glfwSetCursorPosCallback(window, mouseMoveCallback);
    glfwSetMouseButtonCallback(window, mouseButtonCallback);

    GLuint texture = loadTexture("images.bmp");
    if (texture == 0) {
        glfwTerminate();
        return -1;
    }

    shaderProgram = createShaderProgram();
    if (shaderProgram == 0) {
        glfwTerminate();
        return -1;
    }

    for (int i = 0; i < NUM_LODS; i++) {
        initCubeVBO(i);
    }

    glEnable(GL_DEPTH_TEST);

    // Инициализация проекционной матрицы
    projection = glm::perspective(glm::radians(45.0f), (float)windowWidth / windowHeight, 0.1f, 100.0f);

    // Параметры освещения
    glm::vec3 lightPos(10.0f, 10.0f, 10.0f); // Позиция источника света
    glm::vec3 lightColor(1.0f, 1.0f, 1.0f);  // Белый свет
    float ambientStrength = 0.1f;            // Сила фонового освещения
    float diffuseStrength = 0.7f;            // Сила диффузного освещения
    float specularStrength = 0.5f;           // Сила зеркального освещения
    float shininess = 32.0f;                 // Блеск материала

    double lastTime = glfwGetTime();
    while (!glfwWindowShouldClose(window)) {
        double currentTime = glfwGetTime();
        float deltaTime = currentTime - lastTime;
        lastTime = currentTime;

        // Обновление globalDeltaTime для keyCallback
        globalDeltaTime = deltaTime;

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);

        glUseProgram(shaderProgram);

        // Передача проекционной матрицы
        glUniformMatrix4fv(glGetUniformLocation(shaderProgram, "projection"), 1, GL_FALSE, glm::value_ptr(projection));

        // Настройка вида (камеры)
        glm::mat4 view = glm::mat4(1.0f);
        float yawRad = glm::radians(camYaw);
        float pitchRad = glm::radians(camPitch);
        glm::vec3 front(
            sin(yawRad) * cos(pitchRad),
            sin(pitchRad),
            -cos(yawRad) * cos(pitchRad)
        );
        view = glm::lookAt(
            glm::vec3(camPosX, camPosY, camPosZ),
            glm::vec3(camPosX, camPosY, camPosZ) + front,
            glm::vec3(0.0f, 1.0f, 0.0f)
        );
        glUniformMatrix4fv(glGetUniformLocation(shaderProgram, "view"), 1, GL_FALSE, glm::value_ptr(view));

        // Настройка модели
        glm::mat4 model = glm::mat4(1.0f);
        model = glm::translate(model, glm::vec3(posX, posY, posZ));
        model = glm::rotate(model, glm::radians(angleX), glm::vec3(1.0f, 0.0f, 0.0f));
        model = glm::rotate(model, glm::radians(angleY), glm::vec3(0.0f, 1.0f, 0.0f));
        model = glm::scale(model, glm::vec3(scale, scale, scale));
        glUniformMatrix4fv(glGetUniformLocation(shaderProgram, "model"), 1, GL_FALSE, glm::value_ptr(model));

        // Передача параметров освещения
        glUniform3fv(glGetUniformLocation(shaderProgram, "lightPos"), 1, glm::value_ptr(lightPos));
        glUniform3fv(glGetUniformLocation(shaderProgram, "lightColor"), 1, glm::value_ptr(lightColor));
        glUniform3fv(glGetUniformLocation(shaderProgram, "viewPos"), 1, glm::value_ptr(glm::vec3(camPosX, camPosY, camPosZ)));
        glUniform1f(glGetUniformLocation(shaderProgram, "ambientStrength"), ambientStrength);
        glUniform1f(glGetUniformLocation(shaderProgram, "diffuseStrength"), diffuseStrength);
        glUniform1f(glGetUniformLocation(shaderProgram, "specularStrength"), specularStrength);
        glUniform1f(glGetUniformLocation(shaderProgram, "shininess"), shininess);

        // Выбор LOD
        float distance = glm::length(glm::vec3(posX - camPosX, posY - camPosY, posZ - camPosZ));
        int currentLOD = selectLOD(distance);

        // Привязка текстуры и отрисовка
        glBindTexture(GL_TEXTURE_2D, texture);
        drawCube(currentLOD);
        drawUI(currentLOD);

        glfwSwapBuffers(window);
        glfwPollEvents();
    }

    glDeleteTextures(1, &texture);
    glDeleteVertexArrays(NUM_LODS, VAOs);
    glDeleteBuffers(NUM_LODS, VBOs);
    glDeleteBuffers(NUM_LODS, EBOs);
    glDeleteProgram(shaderProgram);

    glfwTerminate();
    return 0;
}

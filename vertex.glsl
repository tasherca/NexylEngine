#version 330 core
layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aTexCoord;
layout (location = 2) in vec3 aNormal;
layout (location = 3) in mat4 instanceModel;
layout (location = 7) in float instanceSelected;
layout (location = 8) in float instanceIsLightSource;
layout (location = 9) in float instanceLightIntensity;
// Новые атрибуты для гизмо
layout (location = 2) in vec3 aColor; // Цвет гизмо (если используется)
layout (location = 3) in float aGizmoType; // Тип гизмо

layout (std140) uniform Matrices {
    mat4 projection;
    mat4 view;
};

out vec2 TexCoord;
out vec3 Normal;
out vec3 FragPos;
out float isSelected;
out float isLightSource;
out float lightIntensity;
// Переменные для гизмо
out vec3 GizmoColor;
out float GizmoType;

void main() {
    mat4 model = instanceModel;
    mat4 mvp = projection * view * model;
    gl_Position = mvp * vec4(aPos, 1.0);
    
    TexCoord = aTexCoord;
    
    mat3 normalMatrix = mat3(transpose(inverse(model)));
    Normal = normalMatrix * aNormal;
    
    FragPos = vec3(model * vec4(aPos, 1.0));
    isSelected = instanceSelected;
    isLightSource = instanceIsLightSource;
    lightIntensity = instanceLightIntensity;
    
    // Передаем данные для гизмо
    GizmoColor = aColor;
    GizmoType = aGizmoType;
}

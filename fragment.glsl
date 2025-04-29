#version 330 core
out vec4 FragColor;

in vec2 TexCoord;
in vec3 Normal;
in vec3 FragPos;
in float isSelected;
in float isLightSource;
// Данные для гизмо
in vec3 GizmoColor;
in float GizmoType;

uniform sampler2D material_diffuse;
uniform vec3 material_specular;
uniform float material_shininess;

uniform vec3 light_position;
uniform vec3 light_color;
uniform float light_ambientStrength;
uniform vec3 light_direction;
uniform int light_type;

uniform vec3 viewPos;
uniform float time;

uniform int isOutline;
uniform float outlineWidth;

void main() {
    // Если это гизмо (GizmoType >= 0), используем фиксированный цвет
    if (GizmoType >= 0.0) {
        FragColor = vec4(GizmoColor, 1.0);
        return;
    }

    // Обычная логика для объектов сцены (кубы, источники света)
    vec3 norm = normalize(Normal);
    vec3 lightDir;
    float diff;
    vec3 diffuse;
    vec3 ambient = light_ambientStrength * light_color;

    if (light_type == 0) { // Point light
        lightDir = normalize(light_position - FragPos);
        diff = max(dot(norm, lightDir), 0.0);
        diffuse = diff * light_color;
    } else if (light_type == 1) { // Directional light
        lightDir = normalize(-light_direction);
        diff = max(dot(norm, lightDir), 0.0);
        diffuse = diff * light_color;
    } else { // Ambient light
        diffuse = vec3(0.0);
    }

    vec3 viewDir = normalize(viewPos - FragPos);
    vec3 reflectDir = reflect(-lightDir, norm);
    float spec = pow(max(dot(viewDir, reflectDir), 0.0), material_shininess);
    vec3 specular = (light_type != 2) ? material_specular * spec * light_color : vec3(0.0);

    vec3 result = (ambient + diffuse + specular) * texture(material_diffuse, TexCoord).rgb;

    if (isLightSource > 0.5) {
        result = light_color; // Источники света используют свой цвет
    }

    if (isOutline == 1) {
        if (isSelected > 0.5) {
            result = vec3(1.0, 1.0, 0.0); // Жёлтый контур для выбранных объектов
        } else {
            discard;
        }
    }

    FragColor = vec4(result, 1.0);
}

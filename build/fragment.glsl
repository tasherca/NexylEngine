#version 330 core
layout (location = 0) out vec4 FragColor;
layout (location = 1) out vec4 BrightColor;

in vec2 TexCoord;
in vec3 Normal;
in vec3 FragPos;
in float isSelected;
in float isLightSource;
in float lightIntensity;

uniform sampler2D material_diffuse;
uniform vec3 material_specular;
uniform float material_shininess;
uniform vec3 light_position;
uniform vec3 light_color;
uniform float light_ambientStrength;
uniform vec3 light_direction;
uniform int light_type;
uniform vec3 viewPos;
uniform bool isOutline;
uniform float outlineWidth;

void main() {
    // Initialize outputs
    FragColor = vec4(0.0);
    BrightColor = vec4(0.0);

    if (isOutline) {
        if (isSelected > 0.5) {
            vec3 normal = normalize(Normal);
            float edge = max(dot(normal, normalize(viewPos - FragPos)), 0.0);
            if (edge < outlineWidth) {
                FragColor = vec4(1.0, 1.0, 0.0, 1.0);
                return;
            }
        }
        discard;
    }

    if (isLightSource > 0.5) {
        // Emissive rendering for light sources with glow
        vec3 emissiveColor = light_color * lightIntensity * 2.0;
        if (isSelected > 0.5) {
            emissiveColor = mix(emissiveColor, vec3(1.0, 0.5, 0.0), 0.3);
        }
        FragColor = vec4(emissiveColor, 1.0);

        // Calculate brightness for bloom
        float brightness = dot(emissiveColor, vec3(0.2126, 0.7152, 0.0722));
        if (brightness > 1.0) {
            BrightColor = vec4(emissiveColor * (brightness - 1.0), 1.0);
        }
        return;
    }

    // Non-light objects (cubes)
    vec3 norm = normalize(Normal);
    vec3 ambient = light_ambientStrength * light_color;
    vec3 result = ambient * texture(material_diffuse, TexCoord).rgb;

    if (light_type != 2) {
        vec3 lightDir = (light_type == 1) ? normalize(-light_direction) : normalize(light_position - FragPos);
        float diff = max(dot(norm, lightDir), 0.0);
        vec3 diffuse = diff * light_color;

        vec3 viewDir = normalize(viewPos - FragPos);
        vec3 reflectDir = reflect(-lightDir, norm);
        float spec = pow(max(dot(viewDir, reflectDir), 0.0), material_shininess);
        vec3 specular = material_specular * spec * light_color;

        result = (ambient + diffuse + specular) * texture(material_diffuse, TexCoord).rgb;
    }

    if (isSelected > 0.5) {
        result = mix(result, vec3(1.0, 0.5, 0.0), 0.3);
    }

    FragColor = vec4(result, 1.0);
}

#version 330 core
out vec4 FragColor;

in vec2 TexCoord;
in vec3 Normal;
in vec3 FragPos;
in float isSelected;

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
    if (isOutline) {
        // Only show outline for selected objects
        if (isSelected > 0.5) {
            // Simple outline effect using normal displacement
            vec3 normal = normalize(Normal);
            float edge = max(dot(normal, normalize(viewPos - FragPos)), 0.0);
            if (edge < outlineWidth) {
                FragColor = vec4(1.0, 1.0, 0.0, 1.0);
                return;
            }
        }
        discard;
    }

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
        // Highlight selected object with orange tint
        result = mix(result, vec3(1.0, 0.5, 0.0), 0.3);
    }

    FragColor = vec4(result, 1.0);
}

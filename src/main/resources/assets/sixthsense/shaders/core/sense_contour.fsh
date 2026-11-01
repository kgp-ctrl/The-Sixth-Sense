#version 150
uniform sampler2D Sampler0;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 stepUV = vec2(max(2.0, floor(size.y / 540.0))) / size;
    float body = texture(Sampler0, texCoord).a;
    float outer = body;
    for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++)
        if (x != 0 || y != 0) outer = max(outer, texture(Sampler0, texCoord + vec2(x,y) * stepUV).a);
    // Exterior silhouette only; no filled body, no vanilla glowing flag or persistent entity mutation.
    fragColor = vec4(0.72, 0.31, 0.40, max(0.0, outer - body));
}

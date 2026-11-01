#version 150
uniform sampler2D Sampler0;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec4 body = texture(Sampler0, texCoord);
    fragColor = vec4(body.a > 0.00001 ? body.rgb / body.a : vec3(0.0), body.a);
}

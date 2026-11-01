#version 150
uniform sampler2D Sampler0;
uniform float GlowAlpha;
uniform vec3 GlowColor;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    if (texture(Sampler0, texCoord).a < 0.1) discard;
    fragColor = vec4(GlowColor * GlowAlpha, GlowAlpha);
}

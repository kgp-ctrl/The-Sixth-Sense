#version 150
uniform sampler2D Sampler0;
uniform float PresenceAlpha;
uniform float NoiseStep;
uniform float Awareness;
in vec2 texCoord;
in vec4 vertexColor;
out vec4 fragColor;
float noise(vec2 cell) {
    vec3 p = fract(vec3(cell.xyx) * 0.1031 + NoiseStep * 0.013);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}
void main() {
    vec4 body = texture(Sampler0, texCoord) * vertexColor;
    if (body.a < 0.1) discard;
    // Texture-space grain follows the animated body instead of swimming behind it.
    float grain = noise(floor(texCoord * vec2(textureSize(Sampler0, 0)) * 6.0));
    if (grain < 0.08 && Awareness < 0.001) discard;
    vec3 staticColor = vec3(grain < 0.46 ? 0.04 : 1.0);
    // Unlit body texture preserves recognizable details even at night. No bloom or outline.
    vec3 contrasted = 0.08 + 0.86 * mix(body.rgb, smoothstep(vec3(0.0), vec3(1.0), body.rgb), 0.45);
    float grainAlpha = grain < 0.08 ? 0.0 : 0.75 + grain * 0.25;
    fragColor = vec4(mix(staticColor, contrasted, Awareness),
                     PresenceAlpha * body.a * mix(grainAlpha, 1.0, Awareness));
}

#version 150
uniform sampler2D Sampler0;
uniform float Strength;
uniform float StaticStrength;
uniform float Blackout;
uniform float NoiseStep;
uniform vec2 ScreenSize;
out vec4 fragColor;
void main() {
    vec2 uv = gl_FragCoord.xy / max(ScreenSize, vec2(1.0));
    vec3 world = texture(Sampler0, uv).rgb;
    float luminance = dot(world, vec3(0.2126, 0.7152, 0.0722));
    vec3 faded = mix(world, vec3(luminance), 0.38 * Strength);
    vec2 cell = floor(gl_FragCoord.xy / 2.0);
    vec3 p = fract(vec3(cell.xyx) * 0.1031 + NoiseStep * 0.013);
    p += dot(p, p.yzx + 33.33);
    float grain = fract((p.x + p.y) * p.z) - 0.5;
    float scan = mod(floor(gl_FragCoord.y), 3.0) < 1.0 ? 0.065 : 0.0;
    vec2 edge = abs(uv - 0.5) * 2.0;
    float vignette = smoothstep(0.60, 1.3, length(edge)) * 0.11;
    faded *= 1.0 - StaticStrength * (scan + vignette);
    faded += vec3(grain * 0.14 * StaticStrength);
    // Top and bottom lids meet at the center; the HUD is rendered afterwards.
    float lid = 0.5 * Blackout;
    float open = Blackout <= 0.0 ? 1.0 : smoothstep(lid, lid + 0.012, min(uv.y, 1.0 - uv.y));
    fragColor = vec4(clamp(faded, 0.0, 1.0) * open, 1.0);
}

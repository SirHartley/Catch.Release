uniform sampler2D noiseTex;

uniform float time;
uniform float alphaMult;

uniform float radius;
uniform float ringWidth;
uniform float feather;

uniform float angularTiling;
uniform float radialTiling;
uniform float noiseScroll;

uniform float noiseCutoff;
uniform float noiseSoft;

uniform vec4 ringColor;

float sstep(float a, float b, float x) {
    float t = clamp((x - a) / (b - a), 0.0, 1.0);
    return t * t * (3.0 - 2.0 * t);
}

void main() {
    vec2 uv = gl_TexCoord[0].xy;
    vec2 p = uv - vec2(0.5, 0.5);
    float r = length(p);

    // Ring mask: band between (radius - ringWidth) and radius
    float outer = 1.0 - sstep(radius - feather, radius + feather, r);
    float inner = sstep(radius - ringWidth - feather, radius - ringWidth + feather, r);
    float ring = outer * inner;

    // Polar coords for noise so breakup follows the circumference
    float ang = atan(p.y, p.x);
    float ang01 = ang / 6.2831853 + 0.5;

    vec2 nUV = vec2(ang01 * angularTiling, r * radialTiling);
    nUV += vec2(time * noiseScroll, time * noiseScroll * 0.73);

    float n = texture2D(noiseTex, nUV).r;

    float cutHard = step(noiseCutoff, n);
    float cutSoft = sstep(noiseCutoff - noiseSoft, noiseCutoff + noiseSoft, n);
    float cut = mix(cutHard, cutSoft, clamp(noiseSoft * 25.0, 0.0, 1.0));

    float a = ring * cut * alphaMult * ringColor.a;
    vec4 col = vec4(ringColor.rgb, a);
    gl_FragColor = col * gl_Color;
}

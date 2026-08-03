uniform sampler2D maskTex;      // unit 1

uniform vec3  glowColor;
uniform float glowAlpha;

uniform float radiusOutPx;      // outer glow length in texels
uniform float radiusInPx;       // inner glow length in texels
uniform vec2  maskTexelSize;    // (1/width, 1/height)

void main() {
    vec2 uv = gl_TexCoord[1].xy;

    // Never tile - outside 0..1 contributes nothing
    float inside =
        step(0.0, uv.x) * step(0.0, uv.y) *
        step(uv.x, 1.0) * step(uv.y, 1.0);

    if (inside <= 0.0) {
        gl_FragColor = vec4(0.0);
        return;
    }

    float a = texture2D(maskTex, uv).a;

    // Force very low alpha to full transparency
    if (a < 0.05) {
        gl_FragColor = vec4(0.0);
        return;
    }

    // Sample pattern (8-tap) for min/max
    // Outer
    vec2 dO = maskTexelSize * radiusOutPx;
    float o1 = texture2D(maskTex, uv + vec2( dO.x, 0.0)).a;
    float o2 = texture2D(maskTex, uv + vec2(-dO.x, 0.0)).a;
    float o3 = texture2D(maskTex, uv + vec2(0.0,  dO.y)).a;
    float o4 = texture2D(maskTex, uv + vec2(0.0, -dO.y)).a;
    float o5 = texture2D(maskTex, uv + vec2( dO.x,  dO.y)).a;
    float o6 = texture2D(maskTex, uv + vec2(-dO.x,  dO.y)).a;
    float o7 = texture2D(maskTex, uv + vec2( dO.x, -dO.y)).a;
    float o8 = texture2D(maskTex, uv + vec2(-dO.x, -dO.y)).a;

    float maxA = max(max(max(o1, o2), max(o3, o4)),
                     max(max(o5, o6), max(o7, o8)));

    // Inner
    vec2 dI = maskTexelSize * radiusInPx;
    float i1 = texture2D(maskTex, uv + vec2( dI.x, 0.0)).a;
    float i2 = texture2D(maskTex, uv + vec2(-dI.x, 0.0)).a;
    float i3 = texture2D(maskTex, uv + vec2(0.0,  dI.y)).a;
    float i4 = texture2D(maskTex, uv + vec2(0.0, -dI.y)).a;
    float i5 = texture2D(maskTex, uv + vec2( dI.x,  dI.y)).a;
    float i6 = texture2D(maskTex, uv + vec2(-dI.x,  dI.y)).a;
    float i7 = texture2D(maskTex, uv + vec2( dI.x, -dI.y)).a;
    float i8 = texture2D(maskTex, uv + vec2(-dI.x, -dI.y)).a;

    float minA = min(min(min(i1, i2), min(i3, i4)),
                     min(min(i5, i6), min(i7, i8)));

    float outer = clamp(maxA - a, 0.0, 1.0);
    float inner = clamp(a - minA, 0.0, 1.0);

    outer = smoothstep(0.02, 0.35, outer);
    inner = smoothstep(0.02, 0.35, inner);

    float glow = outer + inner;

    glow *= (0.25 + 0.75 * a);
    glow *= glowAlpha;

    float outA = glow;
    gl_FragColor = vec4(glowColor * outA, outA);
}

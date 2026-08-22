uniform sampler2D deepTex;

uniform float alphaMult;
uniform float time;

uniform float open;

uniform float seed;
uniform float shards;
uniform float coreSize;
uniform float edgeWidth;

uniform vec3 rimColor;
uniform float rimAlpha;

uniform vec3 deepTint;

uniform vec3 paneColor;
uniform float paneAlpha;

const float TAU = 6.2831853;
const float SIDES = 7.0;

float hash(float n) {
    return fract(sin(n * 127.1 + seed * 311.7) * 43758.5453);
}

float coreRadius(float a01) {
    float k = a01 * SIDES;
    float i0 = floor(k);
    float i1 = i0 + 1.0;

    float t0 = i0 / SIDES * TAU;
    float t1 = i1 / SIDES * TAU;
    float th = a01 * TAU;

    float r0 = coreSize * mix(0.78, 1.18, hash(mod(i0, SIDES) + 3.1));
    float r1 = coreSize * mix(0.78, 1.18, hash(mod(i1, SIDES) + 3.1));

    // Radial distance to the chord keeps polygon sides flat.
    float num = r0 * r1 * sin(t1 - t0);
    float den = r1 * sin(t1 - th) + r0 * sin(th - t0);

    return num / max(den, 1e-4);
}

void main() {
    vec2 uvDeep = gl_TexCoord[0].xy;
    vec2 uvQuad = gl_TexCoord[1].xy;

    vec2 p = uvQuad - vec2(0.5);
    float r = length(p) * 2.0;
    if (r > 1.0) discard;

    float a01 = atan(p.y, p.x) / TAU + 0.5;

    float coreR = coreRadius(a01) * open;

    float b = mod(floor(a01 * shards + 0.5), shards);

    float bendRoll = hash(b + 7.7) - 0.5;
    float kinkRoll = hash(b + 29.3) - 0.5;
    float kinkAt = mix(0.3, 0.6, hash(b + 31.7));
    float lenRoll = hash(b + 13.7);

    float bend = bendRoll * 0.08 * smoothstep(coreR, 1.0, r)
               + kinkRoll * 0.06 * smoothstep(kinkAt, kinkAt + 0.05, r);

    float bAngle = floor(a01 * shards + 0.5) / shards;
    float arc = abs(a01 - bAngle - bend) * TAU * r;

    float crackLen = mix(0.5, 1.05, lenRoll * lenRoll) * open;
    float alongC = clamp((r - coreR) / max(crackLen - coreR, 1e-3), 0.0, 1.0);

    float wCrack = edgeWidth * mix(0.9, 0.15, alongC);

    float crack = (1.0 - smoothstep(wCrack * 0.4, wCrack, arc))
                * (1.0 - smoothstep(crackLen * 0.9, crackLen, r))
                * smoothstep(coreR * 0.7, coreR, r);

    float crackGlow = (1.0 - smoothstep(wCrack, wCrack * 7.0, arc))
                    * (1.0 - alongC)
                    * (1.0 - smoothstep(crackLen * 0.9, crackLen, r))
                    * smoothstep(coreR * 0.7, coreR, r);

    float s = mod(floor(a01 * shards), shards);
    float frac = fract(a01 * shards);

    float lenPane = hash(s + 23.7);
    float tiltRoll = hash(s + 51.3);
    float spreadRoll = hash(s + 77.9);
    float sideRoll = hash(s + 91.4);
    float centerRoll = hash(s + 5.5);

    float paneLen = mix(0.5, 0.95, lenPane) * open;
    float inner = coreR + (0.03 + 0.05 * spreadRoll) * open;
    float spread = mix(0.2, 0.4, spreadRoll);

    float cover = mix(0.38, 1.0, smoothstep(inner, inner + spread, r));
    float across = abs(frac - (0.5 + (centerRoll - 0.5) * 0.2)) * 2.0;

    float inPane = (1.0 - smoothstep(cover - 0.1, cover, across))
                 * smoothstep(inner, inner + 0.02, r)
                 * (1.0 - smoothstep(paneLen * 0.5, paneLen, r));

    float tilt = 0.25 + 0.75 * tiltRoll * tiltRoll;
    float shade = mix(0.8, 1.25, sideRoll > 0.5 ? frac : 1.0 - frac);

    float paneA = paneAlpha * tilt * inPane;

    float edgeIn = inPane * (1.0 - smoothstep(0.0, edgeWidth * 2.0, r - inner));

    float hole = 1.0 - smoothstep(coreR - 0.008, coreR + 0.008, r);

    float nearHole = 1.0 - smoothstep(inner + spread, inner + spread * 1.8, r);
    float gapDark = (1.0 - inPane) * nearHole * smoothstep(coreR - 0.01, coreR + 0.01, r);

    float rimFall = r < coreR ? 40.0 : 16.0;
    float rimGlow = exp(-abs(r - coreR) * rimFall);
    float rimLine = 1.0 - smoothstep(edgeWidth * 0.5, edgeWidth * 1.4, abs(r - coreR));

    vec2 drift = vec2(sin(time * 0.07), cos(time * 0.05)) * 0.01;
    vec3 deep = texture2D(deepTex, uvDeep + drift).rgb * deepTint;

    float deepA = max(hole, gapDark * 0.9);

    vec3 color = deep;
    float alpha = deepA;

    color = mix(color, paneColor * shade, paneA);
    alpha = max(alpha, paneA);

    // Clamp overlapping edge light to avoid a blown-out rim.
    float lit = crack * mix(1.0, 0.35, alongC)
              + crackGlow * 0.2
              + edgeIn * 0.4
              + rimLine * 0.8
              + rimGlow * 0.3;
    lit = min(lit, 1.2);

    color += rimColor * lit * rimAlpha * 0.55;
    alpha = max(alpha, clamp(lit, 0.0, 1.0) * 0.85);

    alpha *= 1.0 - smoothstep(0.92, 1.0, r);

    // Fade near closure to avoid a final-frame pop.
    float closing = smoothstep(0.0, 0.08, open);

    alpha *= alphaMult * closing;
    if (alpha <= 0.004) discard;

    gl_FragColor = vec4(color, alpha);
}

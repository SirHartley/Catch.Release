uniform sampler2D deepTex;   // unit 0 - what shows through the break

uniform float alphaMult;
uniform float time;

// 1 the instant it breaks, 0 once it has closed. Everything about the shape is scaled by it, so the
// fracture heals by retracting rather than by fading out - a crack that dissolves in place reads as
// a decal being turned off, and one that pulls its shards back reads as something closing.
uniform float open;

uniform float seed;
uniform float shards;        // how many panes the sheet breaks into
uniform float coreSize;      // the dark hole in the middle, in quad radii
uniform float edgeWidth;     // how wide a lit break edge is

uniform vec3 rimColor;       // the light coming off every broken edge
uniform float rimAlpha;

// what the deep field is tinted with as it shows through
uniform vec3 deepTint;

// the lifted panes themselves: glass catching the light
uniform vec3 paneColor;
uniform float paneAlpha;

const float TAU = 6.2831853;
const float SIDES = 7.0;

float hash(float n) {
    return fract(sin(n * 127.1 + seed * 311.7) * 43758.5453);
}

// The hole at the middle. Not a circle and not a regular polygon either: each corner sits at its
// own distance, so the opening reads as a shape something made rather than a shape a tool makes.
float coreRadius(float a01) {
    float k = a01 * SIDES;
    float i0 = floor(k);
    float i1 = i0 + 1.0;

    float t0 = i0 / SIDES * TAU;
    float t1 = i1 / SIDES * TAU;
    float th = a01 * TAU;

    float r0 = coreSize * mix(0.78, 1.18, hash(mod(i0, SIDES) + 3.1));
    float r1 = coreSize * mix(0.78, 1.18, hash(mod(i1, SIDES) + 3.1));

    //the distance to the straight chord between the two corners, along this direction - which is
    //what keeps the sides flat instead of bowing between the corners
    float num = r0 * r1 * sin(t1 - t0);
    float den = r1 * sin(t1 - th) + r0 * sin(th - t0);

    return num / max(den, 1e-4);
}

void main() {
    vec2 uvDeep = gl_TexCoord[0].xy;
    vec2 uvQuad = gl_TexCoord[1].xy;

    vec2 p = uvQuad - vec2(0.5);
    float r = length(p) * 2.0;      // 0 at the middle, 1 at the edge of the quad
    if (r > 1.0) discard;

    float a01 = atan(p.y, p.x) / TAU + 0.5;

    float coreR = coreRadius(a01) * open;

    // ------------------------------------------------------------------ the cracks
    // One hairline per pane boundary, running from the hole out. A crack is a line first and a
    // light second: it bends a little as it goes, kinks once somewhere along its run the way real
    // glass does, and a couple of them run much further than the rest.
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

    //widest where it leaves the hole, a hairline by the tip
    float wCrack = edgeWidth * mix(0.9, 0.15, alongC);

    float crack = (1.0 - smoothstep(wCrack * 0.4, wCrack, arc))
                * (1.0 - smoothstep(crackLen * 0.9, crackLen, r))
                * smoothstep(coreR * 0.7, coreR, r);

    //the faint bloom either side of the line, so it reads as lit rather than drawn
    float crackGlow = (1.0 - smoothstep(wCrack, wCrack * 7.0, arc))
                    * (1.0 - alongC)
                    * (1.0 - smoothstep(crackLen * 0.9, crackLen, r))
                    * smoothstep(coreR * 0.7, coreR, r);

    // ------------------------------------------------------------------ the panes
    // The sheet between two cracks, shoved off the hole and tilted. Near the hole a pane covers
    // only the middle of its wedge - the black between panes is the separation - and it widens
    // until pane meets crack. Each catches its own amount of light; one or two catch a lot.
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

    //the edge facing the hole takes the hole's light full on
    float edgeIn = inPane * (1.0 - smoothstep(0.0, edgeWidth * 2.0, r - inner));

    // ------------------------------------------------------------------ the hole and the dark
    float hole = 1.0 - smoothstep(coreR - 0.008, coreR + 0.008, r);

    //the separation: where no pane covers the wedge near the hole, the deep field is what is there
    float nearHole = 1.0 - smoothstep(inner + spread, inner + spread * 1.8, r);
    float gapDark = (1.0 - inPane) * nearHole * smoothstep(coreR - 0.01, coreR + 0.01, r);

    //light spilling out of the hole, and the lit line along its rim. The spill falls off faster
    //inward: the rim is lit, the deep behind it is not
    float rimFall = r < coreR ? 22.0 : 9.0;
    float rimGlow = exp(-abs(r - coreR) * rimFall);
    float rimLine = 1.0 - smoothstep(edgeWidth, edgeWidth * 2.5, abs(r - coreR));

    // ------------------------------------------------------------------ composed
    // The deep field, drifting slowly - what is behind the break is not still
    vec2 drift = vec2(sin(time * 0.07), cos(time * 0.05)) * 0.01;
    vec3 deep = texture2D(deepTex, uvDeep + drift).rgb * deepTint;

    float deepA = max(hole, gapDark * 0.9);

    vec3 color = deep;
    float alpha = deepA;

    //the panes lie over the dark, not under it
    color = mix(color, paneColor * shade, paneA);
    alpha = max(alpha, paneA);

    //and every broken edge is lit
    float lit = crack * mix(1.0, 0.35, alongC)
              + crackGlow * 0.3
              + edgeIn * 0.9
              + rimLine * 1.1
              + rimGlow * 0.7;

    color += rimColor * lit * rimAlpha * 0.6;
    alpha = max(alpha, clamp(lit, 0.0, 1.0) * 0.9);

    //nothing may touch the edge of the quad, or the quad is what shows
    alpha *= 1.0 - smoothstep(0.92, 1.0, r);

    // Fading only at the very end of the heal, so the last sliver does not pop out of existence
    float closing = smoothstep(0.0, 0.08, open);

    alpha *= alphaMult * closing;
    if (alpha <= 0.004) discard;

    gl_FragColor = vec4(color, alpha);
}

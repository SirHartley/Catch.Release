uniform sampler2D deepTex;   // unit 0 - what shows through the break

uniform float alphaMult;
uniform float time;

// 0 the instant it breaks, 1 once it has healed shut. The whole life runs off this one number:
// the rift arrives in the first sliver of it, the teeth spend the middle of it growing into the
// centre, and the last of it fades what is left of them out.
uniform float heal;

uniform float seed;
uniform float shards;        // how many teeth stand around the hole
uniform float coreSize;      // the hole in the middle, in quad radii
uniform float edgeWidth;     // the one width every sharp line is drawn at

uniform vec3 rimColor;       // the hairline along a silhouette's edge - the only light left
uniform float rimAlpha;

// what the deep field is tinted with as it shows through
uniform vec3 deepTint;

// the teeth themselves: near-black silhouette, not glass catching light
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

    //the rift arrives fast and holds its size; the teeth spend the middle of the life advancing
    float grow = smoothstep(0.0, 0.05, heal);
    float close = smoothstep(0.1, 0.92, heal);

    float coreR = coreRadius(a01) * grow;

    // ------------------------------------------------------------------ the teeth
    // One dark wedge per sector, standing around the hole with clear sky between them. A tooth is
    // a silhouette: near-black fill, straight sides, and nothing bright about it but the hairline
    // where it ends. Its inner edge starts at the hole's rim and grows into the centre as the
    // rift heals - the teeth are what close it.
    float s = mod(floor(a01 * shards), shards);
    float frac = fract(a01 * shards);

    float reachRoll = hash(s + 13.7);
    float coverRoll = hash(s + 23.9);
    float centerRoll = hash(s + 5.5);
    float rateRoll = hash(s + 41.3);

    float outR = mix(0.55, 1.0, reachRoll * reachRoll) * grow;
    float cover = mix(0.6, 0.9, coverRoll);
    float across = abs(frac - (0.5 + (centerRoll - 0.5) * 0.15)) * 2.0;

    //distance past the tooth's side in quad radii, so the edge is the same width at any r
    float arcPast = (across - cover) * 0.5 * (TAU / shards) * r;

    //each tooth advances at its own rate - the slowest at exactly 1, so every one of them has
    //reached the middle by the time the rift is shut and none is left standing short of it
    float inR = coreR * (1.0 - clamp(close * mix(1.0, 1.5, rateRoll), 0.0, 1.0));

    float soft = edgeWidth;
    float sideMask = 1.0 - smoothstep(-soft, 0.0, arcPast);
    float radMask = smoothstep(inR, inR + soft, r) * (1.0 - smoothstep(outR - soft, outR, r));
    float tooth = sideMask * radMask;

    // ------------------------------------------------------------------ the hole
    float hole = 1.0 - smoothstep(coreR - soft, coreR, r);
    float holeVis = hole * (1.0 - tooth);

    // ------------------------------------------------------------------ composed
    // The deep field, drifting slowly - what is behind the break is not still
    vec2 drift = vec2(sin(time * 0.07), cos(time * 0.05)) * 0.01;
    vec3 deep = texture2D(deepTex, uvDeep + drift).rgb * deepTint;

    vec3 color = deep;
    float alpha = holeVis * 0.95;

    color = mix(color, paneColor, tooth);
    alpha = max(alpha, tooth * paneAlpha);

    //the only light left: a hairline wherever a silhouette ends. The mask transitions are all one
    //width, so the band this lights is that width and no more
    float lines = (tooth * (1.0 - tooth) + holeVis * (1.0 - holeVis)) * 4.0;
    lines = min(lines, 1.0);

    color += rimColor * lines * rimAlpha * 0.45;
    alpha = max(alpha, lines * 0.6);

    //nothing may touch the edge of the quad, or the quad is what shows
    alpha *= 1.0 - smoothstep(0.92, 1.0, r);

    //in fast, out clean: the teeth meet first, and what is left fades over the last stretch
    float lifeFade = smoothstep(0.0, 0.02, heal) * (1.0 - smoothstep(0.93, 1.0, heal));

    alpha *= alphaMult * lifeFade;
    if (alpha <= 0.004) discard;

    gl_FragColor = vec4(color, alpha);
}

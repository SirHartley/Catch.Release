uniform sampler2D deepTex;   // unit 0 - what shows through the break

uniform float alphaMult;
uniform float time;

// 1 the instant it breaks, 0 once it has closed. Everything about the shape is scaled by it, so the
// fracture heals by retracting rather than by fading out - a crack that dissolves in place reads as
// a decal being turned off, and one that pulls its shards back reads as something closing.
uniform float open;

uniform float seed;
uniform float shards;        // how many spikes go out from the break
uniform float coreSize;      // the dark polygon in the middle, in quad radii
uniform float edgeWidth;     // how wide the lit rim along a break edge is

uniform vec3 rimColor;
uniform float rimAlpha;

// what the deep field is tinted with as it shows through
uniform vec3 deepTint;

const float TAU = 6.2831853;

float hash(float n) {
    return fract(sin(n * 127.1 + seed * 311.7) * 43758.5453);
}

// The polygon at the middle of the break. Not a circle: a circle reads as a hole punched with a
// tool, and this is supposed to have come apart. A few straight sides, at an angle that depends on
// the seed, does it.
float coreRadius(float a01) {
    float sides = 5.0;

    //the angle within this face, running from one corner to the next
    float wedge = (fract(a01 * sides) - 0.5) * TAU / sides;

    //a regular polygon's radius along a direction: the inradius over the cosine of that angle, so
    //the sides come out flat and the corners reach furthest
    return coreSize / cos(wedge);
}

void main() {
    vec2 uvDeep = gl_TexCoord[0].xy;
    vec2 uvQuad = gl_TexCoord[1].xy;

    vec2 p = uvQuad - vec2(0.5);
    float r = length(p) * 2.0;      // 0 at the middle, 1 at the edge of the quad
    if (r > 1.0) discard;

    float a01 = atan(p.y, p.x) / TAU + 0.5;

    // Which spike this direction belongs to, and where across it we are
    float index = floor(a01 * shards);
    float across = fract(a01 * shards) - 0.5;

    float lengthRoll = hash(index);
    float widthRoll = hash(index + 17.3);
    float biasRoll = hash(index + 41.9) - 0.5;

    // A spike is long and thin and comes to a point. Its length is most of what makes one shard
    // differ from the next; the reference is a break where two or three run much further than the
    // rest, so the roll is squared to make long ones uncommon rather than average.
    float len = mix(0.25, 1.0, lengthRoll * lengthRoll) * open;
    float halfWidth = mix(0.08, 0.42, widthRoll);

    // ...narrowing to nothing at the tip, and offset a little so the spikes are not all centred in
    // their own wedge
    float along = clamp(r / max(len, 0.0001), 0.0, 1.0);
    float taper = halfWidth * (1.0 - along * along);
    float offset = biasRoll * 0.3;

    float spike = (1.0 - smoothstep(taper - edgeWidth, taper, abs(across - offset)))
                * (1.0 - smoothstep(len - edgeWidth, len, r));

    float core = 1.0 - smoothstep(coreRadius(a01) * open - edgeWidth, coreRadius(a01) * open, r);

    float broken = clamp(max(spike, core), 0.0, 1.0);
    if (broken <= 0.0) discard;

    // The lit edge of the glass. Strongest exactly at the boundary and gone either side of it, which
    // is what reads as a thickness of broken material rather than a painted outline.
    float rim = broken * (1.0 - broken) * 4.0;

    // The deep field, drifting slowly - what is behind the break is not still
    vec2 drift = vec2(sin(time * 0.07), cos(time * 0.05)) * 0.01;
    vec3 deep = texture2D(deepTex, uvDeep + drift).rgb * deepTint;

    vec3 color = deep + rimColor * rim * rimAlpha;

    // Fading only at the very end of the heal, so the last sliver does not pop out of existence
    float closing = smoothstep(0.0, 0.08, open);

    gl_FragColor = vec4(color, broken * alphaMult * closing);
}

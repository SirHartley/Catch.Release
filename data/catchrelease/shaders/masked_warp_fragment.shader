uniform sampler2D tex;      // unit 0 (fill)
uniform sampler2D maskTex;  // unit 1 (mask)

uniform float alphaMult;
uniform float maskThreshold;
uniform float maskFeather;  // how far over the mask's alpha the rim is softened

// the surface itself
uniform float time;
uniform float waveAmp;      // in fill texcoords, so it scales with the sprite rather than the screen
uniform float waveScale;    // roughly how many waves fit across the fill
uniform float waveSpeed;
uniform float ringScale;    // concentric waves, in rings across the mask's radius
uniform float ringAmp;

// Travelling waves, three of them, at angles and frequencies that do not divide into each other -
// so the surface never repeats on a beat and never lines up into a visible grain.
//
// This is where the movement belongs. It was being done by pushing the corners of a six by six mesh
// about, and six vertices across a pond cannot make a wave: that is one bend, the interpolation
// between the vertices is linear, and the result flexes like a rubber sheet rather than moving like
// water. Per pixel there is no such limit.
vec2 travellingWaves(vec2 uv) {
    float t = time * waveSpeed;
    float s = waveScale * 6.2831853;

    vec2 o = vec2(0.0);

    o.x += sin(uv.y * s + t);
    o.y += cos(uv.x * s * 0.79 - t * 0.83);

    o.x += sin((uv.x + uv.y) * s * 1.7 + t * 1.31) * 0.45;
    o.y += cos((uv.x - uv.y) * s * 1.4 - t * 1.13) * 0.45;

    o.x += sin(uv.y * s * 3.1 - t * 1.9) * 0.18;
    o.y += cos(uv.x * s * 2.7 + t * 2.3) * 0.18;

    return o * waveAmp;
}

// Rings running out from the middle of the mask, which is what says this is a hole with something
// coming up through it rather than a rectangle of moving texture.
vec2 concentricWaves(vec2 uvMask) {
    vec2 fromCenter = uvMask - vec2(0.5);
    float r = length(fromCenter);

    if (r < 0.0001) return vec2(0.0);

    float wave = sin(r * ringScale * 6.2831853 - time * waveSpeed * 2.0);

    // dying off towards the rim, so the edge of the mask is not where the motion is loudest
    float falloff = 1.0 - smoothstep(0.15, 0.5, r);

    return (fromCenter / r) * wave * ringAmp * falloff;
}

void main() {
    vec2 uvFill = gl_TexCoord[0].xy;
    vec2 uvMask = gl_TexCoord[1].xy; // normalized 0..1 in mask space

    // Clamp the mask to a single centered instance by killing samples outside 0..1. Tested before
    // anything is warped, so displacing the fill can never drag the mask's own edge with it.
    if (uvMask.x < 0.0 || uvMask.x > 1.0 || uvMask.y < 0.0 || uvMask.y > 1.0) discard;

    float maskA = texture2D(maskTex, uvMask).a;

    // Feathered rather than cut. A hard step at the threshold left the rim aliased against whatever
    // was behind the pond, which at this size is a visible staircase.
    float edge = smoothstep(maskThreshold, maskThreshold + max(maskFeather, 0.0001), maskA);
    if (edge <= 0.0) discard;

    vec4 fill = texture2D(tex, uvFill + travellingWaves(uvFill) + concentricWaves(uvMask));

    gl_FragColor = vec4(fill.rgb, fill.a * edge * alphaMult);
}

uniform sampler2D tex;      // unit 0 (fill)
uniform sampler2D maskTex;  // unit 1 (mask)

uniform float alphaMult;
uniform float maskThreshold;

// The whirlpool: how many radians the water is twisted by at the drain, and how far the whole
// vortex has turned. Zero twist leaves this shader exactly as it was - the mask does not move,
// only what shows through it.
uniform float swirl;
uniform float swirlSpin;

// a mask-space offset said in fill texcoords, so the twist lands on the right pixels whatever
// the two textures' sizes are
uniform vec2 maskToFill;

void main() {
    vec2 uvFill = gl_TexCoord[0].xy; // pixel-space texcoords
    vec2 uvMask = gl_TexCoord[1].xy; // normalized 0..1 in mask space

    if (swirl != 0.0 || swirlSpin != 0.0) {
        vec2 d = uvMask - vec2(0.5);
        float r = length(d) * 2.0;

        if (r < 1.0) {
            //twist concentrated at the drain, the rim barely turning - squared, so the middle
            //visibly outruns the edge, which is the whole of what reads as a vortex
            float fall = (1.0 - r) * (1.0 - r);
            float theta = swirl * fall + swirlSpin * (0.25 + 0.75 * fall);

            float c = cos(theta);
            float sn = sin(theta);
            vec2 turned = vec2(d.x * c - d.y * sn, d.x * sn + d.y * c);

            uvFill += (turned - d) * maskToFill;
        }
    }

    vec4 fill = texture2D(tex, uvFill);

    // Clamp mask to a single centered instance by killing samples outside 0..1
    float maskA = 0.0;
    if (uvMask.x >= 0.0 && uvMask.x <= 1.0 && uvMask.y >= 0.0 && uvMask.y <= 1.0) {
        maskA = texture2D(maskTex, uvMask).a;
    }

    if (maskA <= maskThreshold) discard;

    gl_FragColor = vec4(fill.rgb, fill.a * maskA * alphaMult);
}

uniform sampler2D tex;      // unit 0 (fill)
uniform sampler2D maskTex;  // unit 1 (mask)

uniform float alphaMult;
uniform float maskThreshold;

// The eddy at the rim: how many radians the water is twisted by at the strongest point of the
// band, the phase of the turn, and where the band starts. Zero twist leaves this shader exactly
// as it was - the mask does not move, only what shows through it.
uniform float swirl;
uniform float swirlSpin;
uniform float swirlEdge;

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
            //a band at the rim and nothing through the middle: the hand-made surface and the warp
            //grid own the water the eye actually looks at, and this only troubles the outside of
            //it. Eased in from swirlEdge and back out again right at the rim, so neither end of
            //the band leaves a seam against the untwisted water beyond it.
            float fall = smoothstep(swirlEdge, 0.92, r) * (1.0 - smoothstep(0.92, 1.0, r));

            //bounded on purpose. An angle that only ever accumulates smears the band into
            //nonsense a few minutes into a session, because the shear across it grows with it -
            //so the turn eases one way and back rather than winding up forever.
            float theta = swirl * fall * sin(swirlSpin);

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

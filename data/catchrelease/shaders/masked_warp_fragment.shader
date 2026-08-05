uniform sampler2D tex;      // unit 0 (fill)
uniform sampler2D maskTex;  // unit 1 (mask)

uniform float alphaMult;
uniform float maskThreshold;

// The hole. well blends a funnel remap of the fill's radius in (0 flat disc, 1 full funnel),
// wellGamma is the exponent of that remap, wellDim how dark the throat goes. swirl is radians
// of twist at the strongest point of the rim band and swirlEdge where that band starts. All
// zero leaves this shader exactly as it was - the mask does not move, only what shows through it.
uniform float swirl;
uniform float swirlEdge;
uniform float well;
uniform float wellGamma;
uniform float wellDim;

// a mask-space offset said in fill texcoords, so the twist lands on the right pixels whatever
// the two textures' sizes are
uniform vec2 maskToFill;

void main() {
    vec2 uvFill = gl_TexCoord[0].xy; // pixel-space texcoords
    vec2 uvMask = gl_TexCoord[1].xy; // normalized 0..1 in mask space

    float dim = 1.0;

    if (swirl != 0.0 || well != 0.0) {
        vec2 d = uvMask - vec2(0.5);
        float r = length(d) * 2.0;

        if (r < 1.0) {
            //the throat is dark because it is far away, gone by two thirds out so the darkening
            //never touches the rim the glow renderer owns. The depth motes draw their own light
            //over this - a darker floor under them is what they were always meant to sit on.
            dim = 1.0 - wellDim * well * (1.0 - smoothstep(0.0, 0.65, r));

            if (r > 0.001) {
                //what makes it a hole: not a rotation but a radial remap. Looking down a funnel,
                //the wall near the throat is close to vertical, so a long run of it compresses
                //into few pixels - r^gamma with gamma under 1 has exactly that ever-steeper
                //compression towards the centre, and lands back on r at the rim, so there is no
                //seam where the remapped fill meets the undisturbed space outside the mask. The
                //remap only ever samples farther out, and the fill extends far past the mask.
                float rt = mix(r, pow(r, wellGamma), well);

                //the eddy: a band at the rim and nothing through the middle, so the funnel and
                //the warp grid own the water the eye actually looks at. Eased in from swirlEdge
                //and back out again right at the rim, so neither end of the band leaves a seam
                //against the untwisted water beyond it.
                float fall = smoothstep(swirlEdge, 0.92, r) * (1.0 - smoothstep(0.92, 1.0, r));

                //bounded on purpose: swirl arrives already modulated by the caller and never
                //accumulates, so the band cannot smear itself into mush over a long session.
                float theta = swirl * fall;

                float c = cos(theta);
                float sn = sin(theta);
                vec2 dr = d * (rt / r);
                vec2 turned = vec2(dr.x * c - dr.y * sn, dr.x * sn + dr.y * c);

                uvFill += (turned - d) * maskToFill;
            }
        }
    }

    vec4 fill = texture2D(tex, uvFill);

    // Clamp mask to a single centered instance by killing samples outside 0..1
    float maskA = 0.0;
    if (uvMask.x >= 0.0 && uvMask.x <= 1.0 && uvMask.y >= 0.0 && uvMask.y <= 1.0) {
        maskA = texture2D(maskTex, uvMask).a;
    }

    if (maskA <= maskThreshold) discard;

    gl_FragColor = vec4(fill.rgb * dim, fill.a * maskA * alphaMult);
}

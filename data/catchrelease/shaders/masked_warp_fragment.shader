uniform sampler2D tex;      // unit 0 (fill)
uniform sampler2D maskTex;  // unit 1 (mask)

uniform float alphaMult;
uniform float maskThreshold;

void main() {
    vec2 uvFill = gl_TexCoord[0].xy; // pixel-space texcoords
    vec2 uvMask = gl_TexCoord[1].xy; // normalized 0..1 in mask space

    vec4 fill = texture2D(tex, uvFill);

    // Clamp mask to a single centered instance by killing samples outside 0..1
    float maskA = 0.0;
    if (uvMask.x >= 0.0 && uvMask.x <= 1.0 && uvMask.y >= 0.0 && uvMask.y <= 1.0) {
        maskA = texture2D(maskTex, uvMask).a;
    }

    if (maskA <= maskThreshold) discard;

    gl_FragColor = vec4(fill.rgb, fill.a * maskA * alphaMult);
}

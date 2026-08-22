uniform sampler2D tex;
uniform sampler2D maskTex;

uniform float alphaMult;
uniform float maskThreshold;

uniform float swirl;
uniform float swirlEdge;
uniform float well;
uniform float wellGamma;
uniform float wellDim;

uniform vec2 maskToFill;

void main() {
    vec2 uvFill = gl_TexCoord[0].xy;
    vec2 uvMask = gl_TexCoord[1].xy;

    float dim = 1.0;

    if (swirl != 0.0 || well != 0.0) {
        vec2 d = uvMask - vec2(0.5);
        float r = length(d) * 2.0;

        if (r < 1.0) {
            dim = 1.0 - wellDim * well * (1.0 - smoothstep(0.0, 0.65, r));

            if (r > 0.001) {
                float rt = mix(r, pow(r, wellGamma), well);

                float fall = smoothstep(swirlEdge, 0.92, r) * (1.0 - smoothstep(0.92, 1.0, r));

                // Swirl is bounded; the caller already modulates it.
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

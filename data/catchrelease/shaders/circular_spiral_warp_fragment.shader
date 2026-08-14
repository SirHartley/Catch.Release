uniform sampler2D tex;
uniform vec2 visibleUV;
uniform vec2 centerUV;
uniform vec2 radiusUV;
uniform float time;
uniform float strength;
uniform float twist;
uniform float motion;
uniform float speed;

// A bounded version of the pond's original centre-weighted whirlpool. Coordinates turn hardest
// near the middle and meet the undisturbed screen at the circle's rim. The motion rocks within a
// fixed angle instead of accumulating, so an hours-long campaign session cannot wind the image
// into an ever-tighter smear.
void main() {
    vec2 uv = gl_TexCoord[0].xy;
    vec2 d = (uv - centerUV) / radiusUV;
    float r = length(d);

    if (r < 1.0) {
        float fall = (1.0 - r) * (1.0 - r);
        float rim = 1.0 - smoothstep(0.86, 1.0, r);
        float phase = sin(time * speed);
        float theta = strength * (twist * fall + motion * phase * (0.25 + 0.75 * fall));

        float c = cos(theta);
        float sn = sin(theta);
        vec2 turned = vec2(d.x * c - d.y * sn, d.x * sn + d.y * c);
        vec2 warped = centerUV + turned * radiusUV;

        uv = mix(uv, warped, rim);
    }

    uv = clamp(uv, vec2(0.0), visibleUV);
    gl_FragColor = texture2D(tex, uv);
}

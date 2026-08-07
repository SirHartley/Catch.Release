uniform sampler2D tex;   // the copied screen
uniform float level;     // overlay strength 0-1; drives the tint
uniform float time;      // seconds, so the warp lives rather than sits
uniform vec3 colorMult;  // the purple, 0-1 rgb
uniform vec2 visibleUV;  // texcoord extent of the visible screen within the buffer
uniform vec2 warp;       // peak displacement in texcoords per axis, already levelled by the caller

// The low-coherence pass: the screen redrawn through a slow sine-field wobble and leaned
// purple. The tint is the radiation-overlay arithmetic - a multiplied lean plus a
// brightness-scaled lift, so lit things bloom and empty space mostly does not - with the
// sampled coordinate displaced first, which is what makes it a warp rather than a wash.
void main() {
    vec2 uv = gl_TexCoord[0].xy;
    vec2 p = uv / visibleUV; // 0-1 across the visible screen

    // two sine octaves per axis, each driven by the other axis's coordinate so no band ever
    // slides along itself; incommensurate frequencies keep the pattern from settling
    vec2 off;
    off.x = sin(p.y * 23.0 + time * 1.7) * 0.6 + sin(p.y * 59.0 - time * 1.1) * 0.4;
    off.y = sin(p.x * 19.0 - time * 1.3) * 0.6 + sin(p.x * 47.0 + time * 0.9) * 0.4;

    // never sample past the visible region - beyond it is stale buffer
    uv = clamp(uv + off * warp, vec2(0.0), visibleUV);

    vec4 col = texture2D(tex, uv);

    float brightness = col.r + col.g + col.b;
    col.rgb *= 1.0 + colorMult * level * 0.5;
    col.rgb += colorMult * level * brightness * 0.35;

    gl_FragColor = col;
}

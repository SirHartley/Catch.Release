uniform sampler2D tex;   // the copied screen
uniform float level;     // overlay strength 0-1; drives the tint
uniform float time;      // seconds, so the warp lives rather than sits
uniform vec3 colorMult;  // the purple, 0-1 rgb
uniform vec2 visibleUV;  // texcoord extent of the visible screen within the buffer
uniform vec2 warp;       // peak displacement in texcoords per axis, already levelled by the caller
uniform float innerClear; // centre-distance the effect may reach in to at level 1, never past

// The low-coherence pass: the screen redrawn through a slow sine-field wobble and leaned
// purple. The tint is the radiation-overlay arithmetic - a multiplied lean plus a
// brightness-scaled lift, so lit things bloom and empty space mostly does not - with the
// sampled coordinate displaced first, which is what makes it a warp rather than a wash.
// Both live under a radial mask: the effect sits at the edges, creeps inward as the level
// rises, and never touches the middle - the fleet has to stay readable through the worst of it.
void main() {
    vec2 uv = gl_TexCoord[0].xy;
    vec2 p = uv / visibleUV; // 0-1 across the visible screen

    // centre distance, deliberately not aspect-corrected: equal-d in p space is an oval on a
    // widescreen, so the clear centre keeps the screen's own shape. 0 centre, 1 mid-edge
    vec2 c = (p - 0.5) * 2.0;
    float d = length(c);

    // edge-pinned at level 0, in to innerClear at level 1 and no further. Held off 1.0: the
    // distance falloffs feeding level can hand over a millionth, and smoothstep with two equal
    // edges divides by zero - one NaN here is the whole screen, since this pass replaces it
    float inner = min(mix(1.0, innerClear, level), 0.999);
    float mask = smoothstep(inner, 1.0, d);

    // two sine octaves per axis, each driven by the other axis's coordinate so no band ever
    // slides along itself; incommensurate frequencies keep the pattern from settling
    vec2 off;
    off.x = sin(p.y * 23.0 + time * 1.7) * 0.6 + sin(p.y * 59.0 - time * 1.1) * 0.4;
    off.y = sin(p.x * 19.0 - time * 1.3) * 0.6 + sin(p.x * 47.0 + time * 0.9) * 0.4;

    // never sample past the visible region - beyond it is stale buffer
    uv = clamp(uv + off * warp * mask, vec2(0.0), visibleUV);

    vec4 col = texture2D(tex, uv);

    float brightness = col.r + col.g + col.b;
    col.rgb *= 1.0 + colorMult * level * 0.5 * mask;
    col.rgb += colorMult * level * brightness * 0.35 * mask;

    gl_FragColor = col;
}

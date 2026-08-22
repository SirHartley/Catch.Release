uniform sampler2D tex;
uniform float level;
uniform float time;
uniform vec3 colorMult;
uniform vec2 visibleUV; // visible part of the backing buffer
uniform vec2 warp;
uniform float innerClear;

void main() {
    vec2 uv = gl_TexCoord[0].xy;
    vec2 p = uv / visibleUV;

    vec2 c = abs((p - 0.5) * 2.0);
    float d = max(c.x, c.y);

    float reach = (1.0 - innerClear) * level;
    float inner = min(1.0 - reach, 0.999);
    float mask = smoothstep(inner, 1.0, d);

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

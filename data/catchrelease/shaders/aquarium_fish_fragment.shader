#version 120

uniform sampler2D tex;
uniform vec4 region;
uniform vec2 texel;
uniform vec2 imageSize;
uniform float strength;
uniform float time;
uniform float phase;

float noise(vec2 cell) {
    return fract(sin(dot(cell, vec2(127.1, 311.7))) * 43758.5453);
}

vec4 sampleFish(vec2 uv) {
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return vec4(0.0);
    // Stay inside this image, including when it occupies only part of a texture.
    uv = clamp(uv, texel * 0.5, vec2(1.0) - texel * 0.5);
    return texture2D(tex, region.xy + uv * region.zw);
}

void main() {
    vec2 uv = (gl_TexCoord[0].xy - region.xy) / region.zw;
    float damage = strength * strength;
    float tick = floor(time * 7.0 + phase * 17.0);
    float burst = smoothstep(0.45, 0.95, sin(time * 2.3 + phase * 5.7));

    float band = floor(uv.y * max(6.0, imageSize.y / 2.0));
    float bandNoise = noise(vec2(band + phase * 31.0, tick));
    float tear = step(0.72 - damage * 0.22, bandNoise) * burst * damage;
    uv.x += (noise(vec2(band, tick + 19.0)) - 0.5)
            * min(8.0 / imageSize.x, 0.18) * tear;

    vec2 cell = floor(uv * imageSize / 3.0);
    float block = step(0.90 - damage * 0.12, noise(cell + vec2(tick, phase * 23.0)))
            * burst * damage;
    vec2 pixels = max(vec2(3.0), imageSize / mix(1.0, 3.0, damage));
    uv = mix(uv, (floor(uv * pixels) + 0.5) / pixels, block);

    float fringe = strength * (1.4 + 1.1 * strength)
            * (0.85 + 0.15 * sin(time * 1.7 + phase));
    vec2 split = vec2(min(fringe / imageSize.x, 0.07),
            min(fringe * 0.18 / imageSize.y, 0.025));
    vec4 red = sampleFish(uv + split);
    vec4 green = sampleFish(uv);
    vec4 blue = sampleFish(uv - split);

    // Reconstruct straight alpha without dark fringes around transparent edges.
    float alpha = max(red.a, max(green.a, blue.a));
    vec3 rgb = vec3(red.r * red.a, green.g * green.a, blue.b * blue.a)
            / max(alpha, 0.0001);
    rgb = mix(rgb, floor(rgb * 7.0 + 0.5) / 7.0, block * 0.65);
    float dropout = 1.0 - block * 0.45;
    gl_FragColor = vec4(rgb * gl_Color.rgb, alpha * dropout * gl_Color.a);
}

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
    float damage = strength * mix(0.5, 1.0, strength);
    float tick = floor(time * 7.0 + phase * 17.0);
    float burst = smoothstep(-0.15, 0.8, sin(time * 2.3 + phase * 5.7));

    float band = floor(uv.y * max(6.0, imageSize.y / 2.0));
    float bandNoise = noise(vec2(band + phase * 31.0, tick));
    float tear = step(0.65 - damage * 0.25, bandNoise) * burst * damage;
    uv.x += (noise(vec2(band, tick + 19.0)) - 0.5)
            * min(12.0 / imageSize.x, 0.25) * tear;

    vec2 cell = floor(uv * imageSize / 3.0);
    float block = step(0.84 - damage * 0.20, noise(cell + vec2(tick, phase * 23.0)))
            * burst * damage;
    vec2 pixels = max(vec2(3.0), imageSize / mix(1.0, 5.0, damage));
    uv = mix(uv, (floor(uv * pixels) + 0.5) / pixels, block);

    vec4 fish = sampleFish(uv);
    vec3 rgb = mix(fish.rgb, floor(fish.rgb * 7.0 + 0.5) / 7.0, block * 0.8);
    float dropout = 1.0 - block * 0.55;
    gl_FragColor = vec4(rgb * gl_Color.rgb, fish.a * dropout * gl_Color.a);
}

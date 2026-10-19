// "HD" anti-aliasing for JL-Modern (GLES2)
//
// 1. manual bilinear magnification of the low-res game framebuffer
//    (removes the blocky nearest-neighbour look, smooths diagonals and text)
// 2. unsharp mask on top: the blur is subtracted back out of the edges, so the
//    picture gets smooth *and* crisp instead of just soft
// 3. optional saturation for a punchier look
//
// Works with either NEAREST or LINEAR sampler settings because the bilinear
// spot is computed by hand in texel space.
precision mediump float;

uniform sampler2D sampler0;
uniform vec2 u_texelDelta;   // 1/gameWidth, 1/gameHeight
uniform vec4 u_setting;      // x=sharpness y=saturation z=gamma w=unused

varying vec2 v_texcoord0;

vec3 tex(vec2 uv) {
    return texture2D(sampler0, uv).rgb;
}

// bilinear fetch centred on the game texels
vec3 fetchBilinear(vec2 uv) {
    vec2 st = uv / u_texelDelta - 0.5;
    vec2 f = fract(st);
    vec2 base = (floor(st) + 0.5) * u_texelDelta;
    vec3 c00 = tex(base);
    vec3 c10 = tex(base + vec2(u_texelDelta.x, 0.0));
    vec3 c01 = tex(base + vec2(0.0, u_texelDelta.y));
    vec3 c11 = tex(base + u_texelDelta);
    return mix(mix(c00, c10, f.x), mix(c01, c11, f.x), f.y);
}

void main() {
    float sharpness  = clamp(u_setting.x, 0.0, 2.0);
    float saturation = clamp(u_setting.y, 0.0, 2.0);

    vec2 uv = v_texcoord0;
    vec3 col = fetchBilinear(uv);

    if (sharpness > 0.0) {
        // blur made of the 4 neighbouring texels, used as the unsharp mask
        vec3 blur = fetchBilinear(uv + vec2(u_texelDelta.x * 0.75, 0.0))
                  + fetchBilinear(uv - vec2(u_texelDelta.x * 0.75, 0.0))
                  + fetchBilinear(uv + vec2(0.0, u_texelDelta.y * 0.75))
                  + fetchBilinear(uv - vec2(0.0, u_texelDelta.y * 0.75));
        blur *= 0.25;
        col += (col - blur) * sharpness;
    }

    if (abs(saturation - 1.0) > 0.01) {
        float l = dot(col, vec3(0.2126, 0.7152, 0.0722));
        col = mix(vec3(l), col, saturation);
    }

    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}

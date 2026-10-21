// Dither for JL-Modern (GLES2)
// "Old monochrome LCD" look: luminance is contrast-pushed, then ordered
// (Bayer 4x4) dithered down to N gray levels — Levels=2 is the pure
// 1-bit black/white of early Nokia / feature-phone screens.
//
// The threshold is evaluated per virtual screen pixel ("Pixel size"),
// so the grain stays fine and regular regardless of how the game is
// stretched; Pixel size > 1 also re-pixelates the picture into chunks
// for an even lower-resolution panel feel.
#extension GL_OES_standard_derivatives : enable
precision mediump float;

uniform sampler2D sampler0;
uniform vec2 u_texelDelta;   // 1/gameWidth, 1/gameHeight
uniform vec2 u_pixelDelta;   // 1/outputWidth, 1/outputHeight
uniform vec4 u_setting;      // x=levels y=palette z=contrast w=pixel size

varying vec2 v_texcoord0;

// Bayer 4x4 ordered threshold, closed form (no bit ops in ES 1.0):
// B4(p) = 4*B2(p mod 2) + B2(floor(p/2)), B2(x,y) = (2x+3y) mod 4
float bayer4(vec2 p) {
    vec2 lo = mod(floor(p), 2.0);
    vec2 hi = mod(floor(floor(p) / 2.0), 2.0);
    float b2lo = mod(2.0 * lo.x + 3.0 * lo.y, 4.0);
    float b2hi = mod(2.0 * hi.x + 3.0 * hi.y, 4.0);
    return (4.0 * b2lo + b2hi + 0.5) / 16.0;
}

void main() {
    float levels   = max(floor(u_setting.x + 0.5), 2.0);
    float palette  = u_setting.y;
    float contrast = clamp(u_setting.z, 0.5, 3.0);
    float px       = max(floor(u_setting.w + 0.5), 1.0);

    vec2 uv = v_texcoord0;
    if (px > 1.5) {
        vec2 duv = vec2(dFdx(v_texcoord0.x), dFdy(v_texcoord0.y));
        vec2 c0 = floor(gl_FragCoord.xy / px) * px;
        uv += (c0 + px * 0.5 - gl_FragCoord.xy) * duv;
        uv = (floor(uv / u_texelDelta) + 0.5) * u_texelDelta;
    }

    vec3 col = texture2D(sampler0, uv).rgb;
    float l = dot(col, vec3(0.2126, 0.7152, 0.0722));
    l = clamp((l - 0.5) * contrast + 0.5, 0.0, 1.0);

    float t = bayer4(gl_FragCoord.xy / px);
    float q = clamp(floor(l * (levels - 1.0) + t), 0.0, levels - 1.0)
            / (levels - 1.0);

    // palette: 0 = plain gray, >0.5 = olive/green monochrome LCD
    vec3 outc = vec3(q);
    if (palette > 0.5) {
        outc = mix(vec3(0.09, 0.12, 0.07), vec3(0.75, 0.78, 0.63), q);
    }

    gl_FragColor = vec4(outc, 1.0);
}

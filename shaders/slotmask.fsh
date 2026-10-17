// Slot mask for JL-Mod (GLES2)
// Based on the OSSC Slot Mask simulator by DariusG
// (RetroArch slang: crt/crt-effects/shaders/slotmask.slang)
//
// Pattern is evaluated in OUTPUT (screen) pixels so it stays a clean, regular
// grille; "Mask size" scales the whole cell, which is the knob to make it
// coarser on high density panels. Applying the mask in squared space and
// mixing it away on bright pixels (threshold) keeps highlights from washing out.
precision mediump float;

uniform sampler2D sampler0;
uniform vec2 u_texelDelta;   // 1/gameWidth, 1/gameHeight
uniform vec4 u_setting;      // x=size(screen px) y=off level z=scanline w=threshold

varying vec2 v_texcoord0;

void main() {
    float size     = max(u_setting.x, 1.0);
    float off      = clamp(u_setting.y, 0.0, 1.0);
    float scan     = clamp(u_setting.z, 0.0, 1.0);
    float thresh   = clamp(u_setting.w, 0.0, 1.0);

    vec3 col = texture2D(sampler0, v_texcoord0).rgb;
    float l = dot(col, vec3(0.22, 0.70, 0.08));

    // slot mask: one cell is 6x3 units, half a cell wide slots staggered per row
    vec2 p = gl_FragCoord.xy / size;
    float x = fract(p.x / 6.0);
    float y = fract(p.y / 3.0);

    vec3 msk = vec3(off);
    if (x < 0.5) {
        if (y > 1.0 / 3.0) {
            if (x < 1.0 / 6.0)        msk.b = 1.0;
            else if (x < 2.0 / 6.0)   msk.g = 1.0;
            else if (x < 3.0 / 6.0)   msk.r = 1.0;
        }
    } else {
        if (y < 2.0 / 3.0) {
            if (x < 4.0 / 6.0)        msk.b = 1.0;
            else if (x < 5.0 / 6.0)   msk.g = 1.0;
            else                      msk.r = 1.0;
        }
    }

    // optional scanlines, one dark band per mask cell
    float sl = 1.0 - scan * 0.45 * step(0.5, fract(p.y / 3.0));

    col = col * col;                                  // gamma 2 working space
    col = mix(col * msk, col, clamp(l * thresh, 0.0, 1.0));
    col = sqrt(col) * sl;                             // back to gamma
    col *= mix(1.0, 1.3, l);                          // bright boost

    gl_FragColor = vec4(col, 1.0);
}

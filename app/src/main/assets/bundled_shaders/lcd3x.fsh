// LCD3x for JL-Modern (GLES2)
// Handheld-LCD look built in SCREEN space so it stays clean at any scale:
// the picture is re-pixelated onto a virtual LCD grid ("Cell size" output
// pixels per cell), each cell gets R/G/B sin-lobe sub-pixel stripes and a
// dark grid gap — like a real handheld panel under a magnifier.
#extension GL_OES_standard_derivatives : enable
precision mediump float;

uniform sampler2D sampler0;
uniform vec2 u_texelDelta;   // 1/gameWidth, 1/gameHeight
uniform vec2 u_pixelDelta;   // 1/outputWidth, 1/outputHeight
uniform vec4 u_setting;      // x=cell px  y=grid gap  z=brightness  w=subpixel strength

varying vec2 v_texcoord0;

// sub-pixel phase offsets (stock lcd3x values)
const vec3 offsets = vec3(3.141592654) * vec3(1.0/2.0, 1.0/2.0 - 2.0/3.0, 1.0/2.0 - 4.0/3.0);

void main() {
    float cell     = max(floor(u_setting.x + 0.5), 2.0);
    float gap      = clamp(u_setting.y, 0.0, 1.0);
    float brighten = clamp(u_setting.z, 0.5, 3.0);
    float sub      = clamp(u_setting.w, 0.0, 1.0);

    // uv change per screen pixel — works for letterboxed/stretched quads too
    vec2 duv = vec2(dFdx(v_texcoord0.x), dFdy(v_texcoord0.y));

    // sample the game at the centre of the virtual cell, then snap the result
    // to the nearest game texel — crisp pixels even when the texture sampler
    // is set to LINEAR ("screen filter" on)
    vec2 cellOrigin = floor(gl_FragCoord.xy / cell) * cell;
    vec2 uv = v_texcoord0 + (cellOrigin + cell * 0.5 - gl_FragCoord.xy) * duv;
    uv = (floor(uv / u_texelDelta) + 0.5) * u_texelDelta;
    vec3 col = texture2D(sampler0, uv).rgb;

    // inside-cell position 0..cell
    vec2 lp = gl_FragCoord.xy - cellOrigin;

    // vertical R/G/B stripes: one sin period across the cell, softened by
    // "Subpixel" — 1.0 = full lcd3x modulation, 0 = flat colour
    vec3 xf = (brighten + sin(lp.x / cell * 6.28318530718 + offsets))
            / (brighten * 0.9 + 0.1);
    col *= mix(vec3(1.0), xf, sub);

    // grid gap: darken the outermost pixel ring of the cell
    vec2 e = min(lp, cell - lp);                  // distance to cell border
    float ring = clamp(min(e.x, e.y), 0.0, 1.0);  // 0 on the border px
    col *= mix(1.0, 0.30 + 0.70 * ring, gap);

    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}

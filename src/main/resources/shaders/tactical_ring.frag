uniform vec4 color;

void main() {
    // Map UV [0,1] to [-1,1] centered
    vec2 uv = gl_TexCoord[0].xy * 2.0 - 1.0;
    float d = length(uv);

    // Ring: fade in from inner edge, fade out past outer edge
    float outer = smoothstep(1.05, 0.92, d);
    float inner = smoothstep(0.60, 0.72, d);
    float ring = outer * inner;

    if (ring < 0.01) discard;
    gl_FragColor = vec4(color.rgb, color.a * ring);
}

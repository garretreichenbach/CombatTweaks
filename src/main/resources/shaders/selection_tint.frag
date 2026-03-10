uniform sampler2D tex;
uniform vec4 tintColor;

void main() {
    vec4 texColor = texture2D(tex, gl_TexCoord[0].st);
    // Blend tint color with original texture
    gl_FragColor = mix(texColor, tintColor, tintColor.a);
}

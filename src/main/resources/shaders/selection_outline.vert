uniform float outlineScale;

void main() {
    // Scale vertex position outward along normal to create outline
    vec3 scaledPos = gl_Vertex.xyz + gl_Normal.xyz * outlineScale;
    gl_Position = gl_ModelViewProjectionMatrix * vec4(scaledPos, 1.0);
    gl_TexCoord[0] = gl_MultiTexCoord0;
}

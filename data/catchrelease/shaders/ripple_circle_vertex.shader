void main() {
    gl_Position = ftransform();
    gl_TexCoord[0] = gl_MultiTexCoord0;
    gl_Color = gl_Color;
    gl_FrontColor = gl_Color;
}

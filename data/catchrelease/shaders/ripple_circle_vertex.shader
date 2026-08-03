void main() {
    gl_Position = ftransform();
    gl_TexCoord[0] = gl_MultiTexCoord0;

    // gl_Color is an attribute in here and is read-only. Assigning to it is a compile error on a
    // strict driver, which takes the whole shader with it - and a silent no-op on a lenient one,
    // which is how it survived this long.
    gl_FrontColor = gl_Color;
}

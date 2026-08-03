void main() {
    gl_Position = ftransform();

    // unit 0 carries the deep field's own texcoords, unit 1 the quad's own 0..1 space - the fracture
    // is built in the second and the first is only ever sampled through it
    gl_TexCoord[0] = gl_MultiTexCoord0;
    gl_TexCoord[1] = gl_MultiTexCoord1;
}

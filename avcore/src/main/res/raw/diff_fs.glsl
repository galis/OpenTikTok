#version 300 es
#ifdef GL_ES
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
#else
#define highp
#define mediump
#define lowp
#endif
in vec2 vTextureCoord;
out vec4 vFragColor;
uniform sampler2D inputImageTexture;
uniform sampler2D blurImageTexture;
void main() {
    vec4 srcColor = texture(inputImageTexture, vTextureCoord);
    vec4 blurColor = texture(blurImageTexture, vTextureCoord);
    vec3 diffColor = (srcColor.rgb - blurColor.rgb) * 7.07;
    diffColor = diffColor * diffColor;
    diffColor.r = min(diffColor.r, 1.0);
    diffColor.g = min(diffColor.g, 1.0);
    diffColor.b = min(diffColor.b, 1.0);
    vFragColor = vec4(diffColor, srcColor.a);
}
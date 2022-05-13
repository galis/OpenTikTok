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
in vec4 textureShift_1;
in vec4 textureShift_2;
in vec4 textureShift_3;
in vec4 textureShift_4;
out vec4 vFragColor;
uniform sampler2D inputImageTexture;
void main() {
    vec4 src = texture(inputImageTexture, vTextureCoord).rgba;
    vec3 sum = src.rgb;
    sum += texture(inputImageTexture, textureShift_1.xy).rgb;
    sum += texture(inputImageTexture, textureShift_1.zw).rgb;
    sum += texture(inputImageTexture, textureShift_2.xy).rgb;
    sum += texture(inputImageTexture, textureShift_2.zw).rgb;
    sum += texture(inputImageTexture, textureShift_3.xy).rgb;
    sum += texture(inputImageTexture, textureShift_3.zw).rgb;
    sum += texture(inputImageTexture, textureShift_4.xy).rgb;
    sum += texture(inputImageTexture, textureShift_4.zw).rgb;
    vFragColor = vec4(sum * 0.1111, src.a);
}

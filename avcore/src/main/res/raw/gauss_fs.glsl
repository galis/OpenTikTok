#version 300 es
precision lowp float;
in vec2 vTextureCoord;
in vec4 textureShift_1;
in vec4 textureShift_2;
in vec4 textureShift_3;
in vec4 textureShift_4;
out vec4 vFragColor;
uniform sampler2D inputImageTexture;
void main() {
    lowp vec4 src = texture(inputImageTexture, vTextureCoord).rgba;
    lowp vec3 sum = src.rgb;
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

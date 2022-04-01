#version 300 es
layout(location = 0) in vec3 aVertCoord;
layout(location = 1) in vec2 aTextureCoord;
out vec2 vTextureCoord;
out vec4 textureShift_1;
out vec4 textureShift_2;
out vec4 textureShift_3;
out vec4 textureShift_4;

//设置参数
uniform float texelWidthOffset;
uniform float texelHeightOffset;

void main() {
    vec2 singleStepOffset = vec2(texelWidthOffset, texelHeightOffset);
    vTextureCoord = aTextureCoord.xy;
    textureShift_1 = vec4(aTextureCoord.xy - singleStepOffset, aTextureCoord.xy + singleStepOffset);
    textureShift_2 = vec4(aTextureCoord.xy - 2.0 * singleStepOffset, aTextureCoord.xy + 2.0 * singleStepOffset);
    textureShift_3 = vec4(aTextureCoord.xy - 3.0 * singleStepOffset, aTextureCoord.xy + 3.0 * singleStepOffset);
    textureShift_4 = vec4(aTextureCoord.xy - 4.0 * singleStepOffset, aTextureCoord.xy + 4.0 * singleStepOffset);
    gl_Position = vec4(aVertCoord.xy, 0.0, 1.0);
}

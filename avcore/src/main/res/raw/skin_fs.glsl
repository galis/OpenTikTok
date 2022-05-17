#version 300 es
precision lowp float;
in vec2 vTextureCoord;
out vec4 vFragColor;

uniform sampler2D inputImageTexture;
uniform sampler2D srcBlurImageTexture;
uniform sampler2D diffBlurImageTexture;
uniform float skin_alpha;

void main() {
    lowp vec4 srcColor = texture(inputImageTexture, vTextureCoord);
    lowp vec3 srcBlurColor = texture(srcBlurImageTexture, vTextureCoord).rgb;
    lowp vec3 diffBlurColor = texture(diffBlurImageTexture, vTextureCoord).rgb;

    lowp float p = clamp((min(srcColor.r, srcBlurColor.r - 0.1) - 0.2) * 4.0, 0.0, 1.0);
    lowp float meanVar = (diffBlurColor.r + diffBlurColor.g + diffBlurColor.b) / 3.0;
    lowp float theta = 0.2;
    lowp float kMin = (1.0 - meanVar / (meanVar + theta)) * skin_alpha;
    lowp vec3 resultColor = mix(srcColor.rgb, srcBlurColor.rgb, max(p, kMin));
    vFragColor = vec4(resultColor, srcColor.a);
}

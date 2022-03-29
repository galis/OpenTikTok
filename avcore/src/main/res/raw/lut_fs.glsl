#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
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
uniform sampler2D lutTexture;
uniform float alpha;

void main(){
    vec4 src = texture(inputImageTexture, vTextureCoord).rgba;
    vec3 lutColor = texture(lutTexture, vTextureCoord).rgb;
    highp float blueColor = src.b * 63.0;
    highp vec2 quad1;
    quad1.y = floor(floor(blueColor) / 8.0);
    quad1.x = floor(blueColor) - (quad1.y * 8.0);
    highp vec2 quad2;
    quad2.y = floor(ceil(blueColor) / 8.0);
    quad2.x = ceil(blueColor) - (quad2.y * 8.0);
    highp vec2 texPos1;
    texPos1.x = (quad1.x * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * src.r);
    texPos1.y = (quad1.y * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * src.g);
    highp vec2 texPos2;
    texPos2.x = (quad2.x * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * src.r);
    texPos2.y = (quad2.y * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * src.g);
    highp vec4 newColor1 = texture(lutTexture, texPos1);
    highp vec4 newColor2 = texture(lutTexture, texPos2);
    highp vec4 newColor = mix(newColor1, newColor2, fract(blueColor));
    vFragColor = vec4(mix(src.rgb, newColor.rgb, alpha), src.a);
}

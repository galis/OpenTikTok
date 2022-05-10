#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
in vec2 vTextureCoord;
out vec4 vFragColor;
uniform vec3 bgColor;
uniform mat3 textureMat;
uniform bool isOes;
uniform sampler2D inputImageTexture;
uniform samplerExternalOES oesImageTexture;

vec4 filterTexture2D(sampler2D targetTexture, vec2 coord){
    if (coord.x < 0.0||coord.x>1.0||coord.y<0.0||coord.y>1.0) {
        return vec4(bgColor, 1.0);
    }
    return texture(targetTexture, coord);
}

vec4 filterTexture2DOes(samplerExternalOES targetTexture, vec2 coord){
    if (coord.x < 0.0||coord.x>1.0||coord.y<0.0||coord.y>1.0) {
        return vec4(bgColor, 1.0);
    }
    return texture(targetTexture, coord);
}

void main(){
    vec2 coord = (textureMat*vec3(vTextureCoord, 1.0)).xy;
    if (isOes){
        vFragColor = filterTexture2DOes(oesImageTexture, vec2(coord.x, 1.0-coord.y));
        return;
    }
    vFragColor = filterTexture2D(inputImageTexture, coord);
}
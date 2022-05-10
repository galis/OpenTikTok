#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
in vec2 vTextureCoord;
out vec4 vFragColor;
uniform vec4 bgColor;
uniform mat3 textureMat;
uniform bool isOes;
uniform bool isFlipVertical;
uniform sampler2D inputImageTexture;
uniform samplerExternalOES oesImageTexture;

void main(){
    vec3 coord = textureMat*vec3(vTextureCoord, 1.0);
    if (coord.x < 0.0||coord.x>1.0||coord.y<0.0||coord.y>1.0) {
        vFragColor = bgColor;
        return;
    }
    if (isOes){
        vFragColor = texture(oesImageTexture, vec2(coord.x, 1.0-coord.y));
        return;
    }
    if (isFlipVertical){
        vFragColor = texture(inputImageTexture, vec2(coord.x, 1.0-coord.y));
        return;
    }
    vFragColor = texture(inputImageTexture, coord.xy);
}
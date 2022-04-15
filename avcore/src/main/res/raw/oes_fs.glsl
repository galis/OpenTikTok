#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
in vec2 vTextureCoord;
out vec4 vFragColor;
uniform samplerExternalOES inputImageTexture;

void main(){
    vFragColor = texture(inputImageTexture, vec2(vTextureCoord.x, 1.0-vTextureCoord.y));
}
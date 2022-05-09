#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
in vec2 vTextureCoord;
out vec4 vFragColor;
uniform samplerExternalOES video0;
uniform samplerExternalOES video1;
uniform float alpha;

void main(){
    vec4 color0 = texture(video0, vec2(vTextureCoord.x, 1.0-vTextureCoord.y));
    vec4 color1 = texture(video1, vec2(vTextureCoord.x, 1.0-vTextureCoord.y));
    vFragColor = vec4(mix(color0.rgb, color1.rgb, alpha), 1.0);
    //    vFragColor = vec4(1.0,0.0,dstColor.b,1.0);
}
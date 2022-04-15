#version 300 es
precision mediump float;
in vec2 vTextureCoord;
out vec4 vFragColor;
uniform sampler2D inputImageTexture;
uniform sampler2D pagTexture;
uniform mat3 pagMat;

void main(){
    //    vec4 pagColor = texture(pagTexture, pagMat*vec3(vTextureCoord, 1.0));
    vec4 srcColor = texture(inputImageTexture, vTextureCoord);
    vec4 pagColor = texture(pagTexture, vec2(vTextureCoord.x, 1.0-vTextureCoord.y));
    pagColor.rgb = pagColor.rgb/pagColor.a;
    vFragColor = vec4(mix(srcColor.rgb, pagColor.rgb, pagColor.a), 1.0);
}
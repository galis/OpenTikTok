#version 300 es
precision mediump float;
in vec2 vTextureCoord;
out vec4 vFragColor;
uniform sampler2D inputImageTexture;
uniform sampler2D pagTexture;
uniform mat3 pagMat;

void main(){
//    vec3 correctCoord = pagMat*vec3(vTextureCoord, 1.0);
    vec2 correctCoord = vTextureCoord;
    vec4 pagColor = texture(pagTexture, vec2(correctCoord.x, 1.0-correctCoord.y));
    if (pagColor.a>0.0){
        pagColor.rgb = pagColor.rgb/pagColor.a;
    }
    vFragColor = vec4(pagColor.rgba);
}
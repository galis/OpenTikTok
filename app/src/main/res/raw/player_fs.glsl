#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;
in vec2 vTextureCoord;
out vec4 vFragColor;
uniform sampler2D inputImageTexture;
uniform sampler2D playerMaskTexture;
uniform mat3 playerMaskMat;

vec4 filterTexture2D(sampler2D targetTexture, vec2 coord){
    if (coord.x < 0.0||coord.x>1.0||coord.y<0.0||coord.y>1.0) {
        return vec4(0.0);
    }
    return texture(targetTexture, coord);
}

void main(){
    vec3 playerCoord = vec3(vTextureCoord, 1.0);
    vec3 playerMaskCoord = playerMaskMat*playerCoord;

    //读取信息
    float alpha = filterTexture2D(playerMaskTexture, vec2(playerMaskCoord.x, 1.0-playerMaskCoord.y)).r;//玩家MASK
    vec4 playerColor = texture(inputImageTexture, playerCoord.xy);//玩家画面
    vFragColor = vec4(playerColor.rgb, alpha);
}
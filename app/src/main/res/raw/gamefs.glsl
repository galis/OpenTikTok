#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision highp float;
in vec2 vTextureCoord;
out vec4 vFragColor;
uniform sampler2D playerTexture;
uniform samplerExternalOES coachTexture;//教练视频
uniform sampler2D screenEffectTexture;
uniform sampler2D playerEffectTexture;
uniform mat3 playerEffectMat;
uniform vec3 bgColor;

vec4 filterTexture2D(sampler2D targetTexture, vec2 coord){
    if (coord.x < 0.0||coord.x>1.0||coord.y<0.0||coord.y>1.0) {
        return vec4(0.0);
    }
    return texture(targetTexture, coord);
}

void main(){
    //计算coord
    float playerShiftX = -0.25;//玩家在画面的偏移
    vec3 playerShiftCoord = vec3(vTextureCoord.x+playerShiftX, vTextureCoord.y, 1.0);
    vec3 playerEffectCoord = playerEffectMat* playerShiftCoord;

    //读取信息
    vec4 playerColor = texture(playerTexture, playerShiftCoord.xy);//玩家画面
    vec4 coachColor = texture(coachTexture, vec2(vTextureCoord.x, 1.0-vTextureCoord.y));//教练Color
    vec4 playerEffectColor = texture(playerEffectTexture, vec2(playerEffectCoord.x, 1.0-playerEffectCoord.y));//用户特效
    //开始合并
    vec3 dstColor = mix(coachColor.rgb, playerColor.rgb+ playerEffectColor.rgb, playerColor.a);//合并教练画面
    vFragColor = vec4(dstColor.rgb, 1.0);
}
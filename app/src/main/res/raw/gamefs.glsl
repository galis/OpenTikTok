#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
in vec2 vTextureCoord;
out vec4 vFragColor;
uniform sampler2D playerTexture;
uniform samplerExternalOES coachTexture;//教练视频
uniform sampler2D playerMaskTexture;
uniform sampler2D screenEffectTexture;
uniform sampler2D playerEffectTexture;
uniform mat3 playerMaskMat;
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
    vec3 playerCoord = playerMaskMat*playerShiftCoord;
    playerCoord.y = 1.0-playerCoord.y;
    vec3 playerEffectCoord = playerEffectMat* playerShiftCoord;
    playerEffectCoord.y = 1.0-playerEffectCoord.y;
    vec2 normalCoord = vec2(vTextureCoord.x, 1.0-vTextureCoord.y);
    //读取信息
    float alpha = filterTexture2D(playerMaskTexture, playerCoord.xy).r;
    vec4 playerColor = texture(playerTexture, vec2(playerShiftCoord.x, 1.0-playerShiftCoord.y));//玩家画面
    vec4 coachColor = texture(coachTexture, normalCoord);//教练画面
    vec4 screenEffectColor = texture(screenEffectTexture, normalCoord);//特效画面
    vec4 playerEffectColor = texture(playerEffectTexture, playerEffectCoord.xy);//用户特效
    //开始合并
    vec3 dstColor = mix(coachColor.rgb, playerColor.rgb, alpha);//合并教练画面
    dstColor = mix(dstColor.rgb, dstColor.rgb+ playerEffectColor.rgb, alpha);//合并教练画面
    dstColor = mix(dstColor.rgb, screenEffectColor.rgb, screenEffectColor.a);//合并全屏动效
    vFragColor = vec4(dstColor.rgb, 1.0);
}
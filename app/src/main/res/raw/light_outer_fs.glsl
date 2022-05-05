#version 300 es
precision lowp float;
in vec2 vTextureCoord;
out vec4 vFragColor;
uniform sampler2D inputImageTexture;
uniform vec2 fboSize;// viewport resolution (in pixels)

precision highp float;

const float dist = 0.0;
const float angle = 0.0;
const vec4 color = vec4(0.7, 0.0, 0.4, 0.0);
const float alpha = 1.0;
const float blurX = 90.0;
const float blurY = 90.0;
// uniform vec4 quality;
const float strength = 15.0;
const float inner = 0.0;
const float knockout = 0.5;
const float hideObject = 0.0;

const float linearSamplingTimes = 7.0;
const float circleSamplingTimes = 12.0;
const float PI = 3.14159265358979323846264;

float calc_alpha(vec4 color){
    return color.a;
}

float random(vec2 fragCoord, vec2 scale)
{
    return fract(sin(dot(fragCoord.xy, scale)) * 43758.5453);
}

void main(){
    vec2 px = vec2(1.0 / fboSize.x, 1.0 / fboSize.y);
    vec4 ownColor = texture(inputImageTexture, vTextureCoord);

    if (calc_alpha(ownColor)==0.0) {
        ownColor = vec4(0.0);
    }

    vec4 curColor;
    float totalAlpha = 0.0;
    float maxTotalAlpha = 0.0;
    float curDistanceX = 0.0;
    float curDistanceY = 0.0;
    float offsetX = dist*px.x * cos(angle);
    float offsetY = dist*px.y* sin(angle);

    float cosAngle;
    float sinAngle;
    float offset = PI * 2.0 / circleSamplingTimes * random(vTextureCoord, vec2(12.9898, 78.233));

    float stepX = blurX * px.x / linearSamplingTimes;//线性采样
    float stepY = blurY * px.y / linearSamplingTimes;

    if (ownColor.a>0.5){
        vFragColor = ownColor;
        return;
    }

    //循环角采样
    for (float a = 0.0; a <= PI * 2.0; a += PI * 2.0 / circleSamplingTimes) {
        cosAngle = cos(a + offset);
        sinAngle = sin(a + offset);
        //循线性采样
        for (float i = 1.0; i <= linearSamplingTimes; i++) {
            curDistanceX = i * stepX * cosAngle;
            curDistanceY = i * stepY * sinAngle;
            vec2 uv = vec2(vTextureCoord.x + curDistanceX - offsetX, vTextureCoord.y + curDistanceY + offsetY);
            if (uv.x >= 0.0 && uv.x <= 1.0 && uv.y >= 0.0 && uv.y <= 1.0){
                //采集采样点的alpha
                curColor = texture(inputImageTexture, uv);
                totalAlpha += (linearSamplingTimes - i) * calc_alpha(curColor);
            }
            maxTotalAlpha += (linearSamplingTimes - i);
        }
    }

    //计算平均alpha
    totalAlpha = totalAlpha/maxTotalAlpha;

//    if (totalAlpha<0.1){
//        vFragColor = ownColor;
//        return;
//    }

    float sc = totalAlpha*totalAlpha * strength;
    vec4 resultColor = color*sc;
    vFragColor = vec4(resultColor.rgb, sc);
    //    vFragColor = vec4(1.0);
}
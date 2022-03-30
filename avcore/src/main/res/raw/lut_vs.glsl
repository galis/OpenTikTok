#version 300 es
layout(location = 0) in vec3 aVertCoord;
layout(location = 1) in vec2 aTextureCoord;
out vec2 vTextureCoord;
out vec2 vlutTextureCoord;
void main(){
    vTextureCoord = aTextureCoord;
    vlutTextureCoord = aTextureCoord;
    vlutTextureCoord.y = 1.0-vlutTextureCoord.y;
    gl_Position = vec4(aVertCoord.xy, 0.0, 1.0);
}
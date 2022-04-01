#version 300 es
layout(location = 0) in vec3 aVertCoord;
layout(location = 1) in vec2 aTextureCoord;
out vec2 vTextureCoord;
void main(){
    vTextureCoord = aTextureCoord;
    gl_Position = vec4(aVertCoord.xy, 0.0, 1.0);
}
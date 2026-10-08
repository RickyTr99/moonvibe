#version 450
#extension GL_GOOGLE_include_directive : require

// Full range YCbCr 4:2:0 in separate Y, Cb and Cr planes, as PyroWave decodes it
layout(set = 0, binding = 0) uniform sampler2D uPlanes[3];

#include "video_common.glsl"

layout(location = 0) in vec2 vUv;
layout(location = 0) out vec4 outColor;

vec3 fetchRgb(vec2 uv) {
    uv = clamp(uv, pc.uvClamp.xy, pc.uvClamp.zw);
    float y = textureLod(uPlanes[0], uv, 0.0).r;

    // The host sites chroma with the left luma sample of each pair, not between them
    vec2 chromaUv = uv + vec2(pc.ycbcr.w, 0.0);
    float cb = textureLod(uPlanes[1], chromaUv, 0.0).r - pc.ycbcr.z;
    float cr = textureLod(uPlanes[2], chromaUv, 0.0).r - pc.ycbcr.z;

    float kr = pc.ycbcr.x;
    float kb = pc.ycbcr.y;
    float r = y + 2.0 * (1.0 - kr) * cr;
    float b = y + 2.0 * (1.0 - kb) * cb;
    float g = (y - kr * r - kb * b) / (1.0 - kr - kb);
    return vec3(r, g, b);
}

// The Y plane alone: one sample per tap
float fetchLuma(vec2 uv) {
    return textureLod(uPlanes[0], clamp(uv, pc.uvClamp.xy, pc.uvClamp.zw), 0.0).r;
}

#include "sharpen.glsl"

void main() {
    outColor = outputColor(sharpenedColor(vUv));
}

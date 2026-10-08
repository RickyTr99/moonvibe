#version 450
#extension GL_GOOGLE_include_directive : require

// The sampler carries an immutable VkSamplerYcbcrConversion, so sampling returns
// non-linear R'G'B' in the stream's own color encoding.
layout(set = 0, binding = 0) uniform sampler2D uVideo;

#include "video_common.glsl"

layout(location = 0) in vec2 vUv;
layout(location = 0) out vec4 outColor;

vec3 fetchRgb(vec2 uv) {
    return textureLod(uVideo, clamp(uv, pc.uvClamp.xy, pc.uvClamp.zw), 0.0).rgb;
}

// Near enough to the stream's own luma to find edges by. Its weights sum to 1, so a change
// added to all three channels changes it by as much.
float fetchLuma(vec2 uv) {
    return dot(fetchRgb(uv), vec3(0.2126, 0.7152, 0.0722));
}

#include "sharpen.glsl"

void main() {
    outColor = outputColor(sharpenedColor(vUv));
}

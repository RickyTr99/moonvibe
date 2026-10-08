// Sharpening, to win back the fine detail the stream softens. It works on luma only, so edges
// don't pick up color fringes, and adds the change to all three channels. The fragment shader
// that includes this defines fetchRgb() and fetchLuma(), both clamped to the picture.

vec3 fetchRgb(vec2 uv);
float fetchLuma(vec2 uv);

// Luma of the texel offset texels away from uv
float tap(vec2 uv, float dx, float dy) {
    return fetchLuma(uv + vec2(dx, dy) * pc.texel.xy);
}

// AMD FidelityFX CAS at full sharpness, without scaling
// Copyright (c) 2020 Advanced Micro Devices, Inc. SPDX-License-Identifier: MIT
// Sharpens most where local contrast is low, and little where an edge is already hard.
float casDelta(vec2 uv, float e) {
    float a = tap(uv, -1.0, -1.0);
    float b = tap(uv, 0.0, -1.0);
    float c = tap(uv, 1.0, -1.0);
    float d = tap(uv, -1.0, 0.0);
    float f = tap(uv, 1.0, 0.0);
    float g = tap(uv, -1.0, 1.0);
    float h = tap(uv, 0.0, 1.0);
    float i = tap(uv, 1.0, 1.0);

    float mnCross = min(min(min(b, d), min(e, f)), h);
    float mn = mnCross + min(mnCross, min(min(a, c), min(g, i)));
    float mxCross = max(max(max(b, d), max(e, f)), h);
    float mx = mxCross + max(mxCross, max(max(a, c), max(g, i)));

    float amp = sqrt(clamp(min(mn, 2.0 - mx) / max(mx, 1e-5), 0.0, 1.0));
    // -1 / mix(8, 5, sharpness)
    float w = amp * -0.2;
    return ((b + d + f + h) * w + e) / (1.0 + 4.0 * w) - e;
}

// The picture at uv, sharpened by the strength in the push constants: 0 leaves it alone, 1 is
// CAS at full sharpness. Left of the comparison split it stays as it came.
vec3 sharpenedColor(vec2 uv) {
    vec3 color = fetchRgb(uv);
    float strength = pc.sharpen.x;
    if (strength <= 0.0 || uv.x < pc.sharpen.y) {
        return color;
    }
    return clamp(color + casDelta(uv, fetchLuma(uv)) * strength, 0.0, 1.0);
}

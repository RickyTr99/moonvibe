package com.limelight.binding.video;

/**
 * The numbers of the performance overlay for the last second or two, as the decoder measured them.
 * A value that is not known is negative (or null for text).
 */
public class PerfStats {
    public String decoder;
    // The renderer's fixed details (Vulkan output and path), null without it
    public String renderer;
    public int width, height;
    public boolean hdr;

    // Frame rate of the stream, frames received from the network and frames shown
    public float streamFps, receivedFps, renderedFps;
    // Shown against received, in percent, smoothed
    public float fpsVariance;

    public float droppedPercent;
    // Frames that arrived with gaps (PyroWave only), -1 otherwise
    public float partialPercent = -1;

    public int rttMs, rttVarianceMs;
    public float bandwidthMbps = -1;

    public float hostLatencyMin = -1, hostLatencyMax = -1, hostLatencyAvg = -1;
    public float decodeMs;

    // From the Vulkan renderer: the buffer and refresh rate, then lock state and skipped frames
    public String pacingHeadline, pacingDetails;

    /**
     * From a button press to its frame ready on the device: the input goes to the host and the frame comes back
     * (the whole round trip), plus the host's processing and the decoding. The display adds its own on top.
     */
    public float totalLatencyMs() {
        return Math.max(0, hostLatencyAvg) + rttMs + decodeMs;
    }
}

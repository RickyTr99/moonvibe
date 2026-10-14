package com.limelight.nvstream.http;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * The host's CPU and GPU readings (Rubylight's hardware metrics, version 1). Each reading may be missing:
 * a negative value is one the host did not send.
 */
public class HostMetrics {
    public float cpuPercent = -1;
    public String gpuName;
    public float gpuPercent = -1;
    public float gpuTempC = -1;
    public float gpuPowerW = -1;
    public float vramUsedMb = -1;
    // How long the host waits between samples
    public int intervalMs = 1000;

    public static HostMetrics parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (root.optInt("version", 0) != 1) {
            throw new JSONException("Unsupported hardware metrics version");
        }
        HostMetrics metrics = new HostMetrics();
        metrics.intervalMs = Math.max(500, root.optInt("interval_ms", 1000));

        JSONObject cpu = root.optJSONObject("cpu");
        if (cpu != null) {
            metrics.cpuPercent = reading(cpu, "cpu_usage_percent");
        }

        // The list is every AMD GPU of the PC, not the one that encodes: the busiest is the one that matters
        JSONArray gpus = root.optJSONArray("gpus");
        JSONObject busiest = null;
        String busiestName = null;
        float busiestUsage = -2;
        if (gpus != null) {
            for (int i = 0; i < gpus.length(); i++) {
                JSONObject gpu = gpus.optJSONObject(i);
                JSONObject readings = gpu != null ? gpu.optJSONObject("metrics") : null;
                if (readings == null) {
                    continue;
                }
                float usage = reading(readings, "gpu_usage_percent");
                if (usage > busiestUsage) {
                    busiest = readings;
                    busiestName = gpu.optString("name", null);
                    busiestUsage = usage;
                }
            }
        }
        if (busiest != null) {
            metrics.gpuName = busiestName;
            metrics.gpuPercent = reading(busiest, "gpu_usage_percent");
            metrics.gpuTempC = reading(busiest, "gpu_temperature_c");
            // The whole board, as the card's rated power counts it; the chip alone otherwise
            metrics.gpuPowerW = reading(busiest, "gpu_board_power_w");
            if (metrics.gpuPowerW < 0) {
                metrics.gpuPowerW = reading(busiest, "gpu_power_w");
            }
            metrics.vramUsedMb = reading(busiest, "gpu_vram_used_mb");
        }
        return metrics;
    }

    private static float reading(JSONObject readings, String name) {
        double value = readings.optDouble(name, Double.NaN);
        return Double.isNaN(value) || value < 0 ? -1 : (float) value;
    }
}

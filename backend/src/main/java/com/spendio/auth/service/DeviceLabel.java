package com.spendio.auth.service;

/**
 * Best-effort user-agent to a short device label for the session list
 * (ARCHITECTURE.md D.2, K.1 "device/IP/last-used").
 */
final class DeviceLabel {

    private DeviceLabel() {
    }

    static String from(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown device";
        }
        String browser = "Browser";
        String lower = userAgent.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("edg/") || lower.contains("edge/")) {
            browser = "Edge";
        } else if (lower.contains("chrome") && !lower.contains("chromium")) {
            browser = "Chrome";
        } else if (lower.contains("firefox")) {
            browser = "Firefox";
        } else if (lower.contains("safari") && !lower.contains("chrome")) {
            browser = "Safari";
        } else if (lower.contains("curl")) {
            browser = "curl";
        }

        String os = "Device";
        if (lower.contains("windows")) {
            os = "Windows";
        } else if (lower.contains("mac os") || lower.contains("macintosh") || lower.contains("darwin")) {
            os = "macOS";
        } else if (lower.contains("android")) {
            os = "Android";
        } else if (lower.contains("iphone") || lower.contains("ipad") || lower.contains("ios")) {
            os = "iOS";
        } else if (lower.contains("linux")) {
            os = "Linux";
        }

        String label = browser + " on " + os;
        return label.length() > 80 ? label.substring(0, 80) : label;
    }
}

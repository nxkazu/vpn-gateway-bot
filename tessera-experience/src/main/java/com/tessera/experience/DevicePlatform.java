package com.tessera.experience;

import java.util.Arrays;

/** Supported setup-guide platforms shown in the user-facing VPN hub. */
public enum DevicePlatform {
    IOS("ios", "setup.platform.ios", "setup.guide.ios"),
    ANDROID("android", "setup.platform.android", "setup.guide.android"),
    WINDOWS("windows", "setup.platform.windows", "setup.guide.windows"),
    MACOS("macos", "setup.platform.macos", "setup.guide.macos"),
    TV("tv", "setup.platform.tv", "setup.guide.tv");

    private final String code;
    private final String titleKey;
    private final String guideKey;

    DevicePlatform(String code, String titleKey, String guideKey) {
        this.code = code;
        this.titleKey = titleKey;
        this.guideKey = guideKey;
    }

    public String code() { return code; }
    public String titleKey() { return titleKey; }
    public String guideKey() { return guideKey; }

    public static DevicePlatform fromCode(String code) {
        return Arrays.stream(values())
                .filter(p -> p.code.equalsIgnoreCase(code == null ? "" : code))
                .findFirst()
                .orElse(ANDROID);
    }
}

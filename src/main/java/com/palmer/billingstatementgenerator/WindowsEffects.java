package com.palmer.billingstatementgenerator;

import com.sun.glass.ui.Window;
import com.sun.jna.*;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class WindowsEffects {

    private static final Logger log = LoggerFactory.getLogger(WindowsEffects.class);

    private static final int DWMWA_USE_IMMERSIVE_DARK_MODE = 20;
    private static final int DWMWA_WINDOW_CORNER_PREFERENCE = 33;
    private static final int DWMWCP_ROUND = 2;
    private static final int DWMWA_SYSTEMBACKDROP_TYPE = 38;
    private static final int DWMSBT_MAINWINDOW = 2; // Mica

    private WindowsEffects() {
    }

    public static void apply(Stage stage) {
        if (!isWindows()) {
            log.debug("Not Windows, skipping effects");
            return;
        }
        try {
            WinDef.HWND hwnd = getHwnd(stage);
            log.debug("FindWindow result for '{}': {}", stage.getTitle(), hwnd);
            if (hwnd == null) {
                return;
            }
            if (isWindows11()) {
                enableDarkMode(hwnd);
                setIntAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, 1);
                setIntAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, DWMWCP_ROUND);
                setIntAttribute(hwnd, DWMWA_SYSTEMBACKDROP_TYPE, DWMSBT_MAINWINDOW);
            }
            else {
                log.debug("Applying Windows 10 effects");
                applyWindows10(hwnd);
            }
        } catch (Exception e) {
            log.warn("Failed to apply Windows effects", e);
        }
    }

    private static WinDef.HWND getHwnd(Stage stage) {
        for (Window w : Window.getWindows()) {
            if (stage.getTitle().equals(w.getTitle())) {
                long handle = w.getNativeHandle();
                log.debug("Glass HWND for '{}': 0x{}", stage.getTitle(), Long.toHexString(handle));
                return new WinDef.HWND(Pointer.createConstant(handle));
            }
        }
        log.debug("Glass HWND not found for '{}'", stage.getTitle());
        return null;
    }

    private static void setIntAttribute(WinDef.HWND hwnd, int attribute, int value) {
        IntByReference ref = new IntByReference(value);
        int result = Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, attribute, ref.getPointer(), 4);
        log.debug("DwmSetWindowAttribute attr={} value={} result=0x{}", attribute, value, Integer.toHexString(result));
    }

    private static void enableDarkMode(WinDef.HWND hwnd) {
        try {
            WinDef.HMODULE hMod = Kernel32Ex.INSTANCE.GetModuleHandleA("uxtheme");
            Pointer setPreferredAppMode = Kernel32Ex.INSTANCE.GetProcAddress(hMod, Pointer.createConstant(135));
            Pointer allowDarkModeForWindow = Kernel32Ex.INSTANCE.GetProcAddress(hMod, Pointer.createConstant(133));

            if (setPreferredAppMode != null) {
                Function.getFunction(setPreferredAppMode, Function.ALT_CONVENTION).invoke(new Object[]{2});
            }
            if (allowDarkModeForWindow != null) {
                Function.getFunction(allowDarkModeForWindow, Function.ALT_CONVENTION).invoke(new Object[]{hwnd, true});
            }
        } catch (Exception e) {
            log.warn("Failed to enable dark mode via uxtheme", e);
        }
    }

    private static void applyWindows10(WinDef.HWND hwnd) {
        // ACCENT_ENABLE_ACRYLICBLURBEHIND = 4
        // Available on Windows 10 1903+
        AccentPolicy accent = new AccentPolicy();
        accent.AccentState = 4;
        accent.AccentFlags = 2;
        accent.GradientColor = 0x00000000;
        accent.write();

        WindowCompositionAttributeData data = new WindowCompositionAttributeData();
        data.Attribute = 19; // WCA_ACCENT_POLICY
        data.Data = accent.getPointer();
        data.SizeOfData = accent.size();

        User32Ex.INSTANCE.SetWindowCompositionAttribute(hwnd, data);
    }

    private static boolean isWindows11() {
        if (!isWindows()) {
            return false;
        }

        try {
            Process process = Runtime.getRuntime().exec(
                    new String[]{"reg", "query",
                            "HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
                            "/v", "CurrentBuildNumber"}
            );
            String output = new String(process.getInputStream().readAllBytes());
            String[] parts = output.trim().split("\\s+");
            int build = Integer.parseInt(parts[parts.length - 1]);
            log.debug("Windows build number: {}", build);
            return build >= 22000;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
    }

    public static void init() {
        if (!isWindows()) {
            return;
        }

        try {
            WinDef.HMODULE hMod = Kernel32Ex.INSTANCE.GetModuleHandleA("uxtheme");
            log.debug("uxtheme module: {}", hMod);
            Pointer fn = Kernel32Ex.INSTANCE.GetProcAddress(hMod, Pointer.createConstant(135));
            log.debug("SetPreferredAppMode ptr: {}", fn);
            if (fn != null) {
                Function.getFunction(fn, Function.ALT_CONVENTION).invoke(new Object[]{2});
                log.debug("SetPreferredAppMode called successfully");
            } else {
                log.warn("SetPreferredAppMode not found at ordinal 135");
            }
        } catch (Exception e) {
            log.warn("Failed to init dark mode", e);
        }
    }

    private interface Dwmapi extends StdCallLibrary {
        Dwmapi INSTANCE = Native.load("dwmapi", Dwmapi.class);

        int DwmGetWindowAttribute(WinDef.HWND hwnd, int dwAttribute, Pointer pvAttribute, int cbAttribute);

        int DwmSetWindowAttribute(WinDef.HWND hwnd, int dwAttribute, Pointer pvAttribute, int cbAttribute);
    }

    private interface User32Ex extends StdCallLibrary {
        User32Ex INSTANCE = Native.load("user32", User32Ex.class);

        boolean SetWindowCompositionAttribute(WinDef.HWND hwnd, WindowCompositionAttributeData data);
    }

    private interface Kernel32Ex extends StdCallLibrary {
        Kernel32Ex INSTANCE = Native.load("kernel32", Kernel32Ex.class);
        WinDef.HMODULE GetModuleHandleA(String moduleName);
        Pointer GetProcAddress(WinDef.HMODULE hModule, Pointer procName);
    }

    @Structure.FieldOrder({"Attribute", "Data", "SizeOfData"})
    public static class WindowCompositionAttributeData extends Structure {
        public int Attribute;
        public Pointer Data;
        public int SizeOfData;
    }

    @Structure.FieldOrder({"AccentState", "AccentFlags", "GradientColor", "AnimationId"})
    public static class AccentPolicy extends Structure {
        public int AccentState;
        public int AccentFlags;
        public int GradientColor;
        public int AnimationId;
    }
}
package com.palmer.billingstatementgenerator;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
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

    private WindowsEffects() {
    }

    public static void apply(Stage stage) {
        if (!isWindows()) {
            return;
        }
        try {
            WinDef.HWND hwnd = User32.INSTANCE.FindWindow(null, stage.getTitle());
            if (hwnd == null) {
                return;
            }
            if (isWindows11()) {
                setIntAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, 1);
                setIntAttribute(hwnd, DWMWA_WINDOW_CORNER_PREFERENCE, DWMWCP_ROUND);
            } else {
                applyWindows10(hwnd);
            }
        } catch (Exception e) {
            log.warn("Failed to apply Windows effects", e);
        }
    }

    private static void setIntAttribute(WinDef.HWND hwnd, int attribute, int value) {
        IntByReference ref = new IntByReference(value);
        Dwmapi.INSTANCE.DwmSetWindowAttribute(hwnd, attribute, ref.getPointer(), 4);
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
            return build >= 22000;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("windows");
    }

    private interface Dwmapi extends StdCallLibrary {
        Dwmapi INSTANCE = Native.load("dwmapi", Dwmapi.class);

        int DwmSetWindowAttribute(WinDef.HWND hwnd, int dwAttribute, Pointer pvAttribute, int cbAttribute);
    }

    private interface User32Ex extends StdCallLibrary {
        User32Ex INSTANCE = Native.load("user32", User32Ex.class);

        boolean SetWindowCompositionAttribute(WinDef.HWND hwnd, WindowCompositionAttributeData data);
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
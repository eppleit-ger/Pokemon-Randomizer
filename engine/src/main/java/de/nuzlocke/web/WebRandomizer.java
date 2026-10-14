package de.nuzlocke.web;

import com.dabomstew.pkrandom.FileFunctions;
import com.dabomstew.pkrandom.RandomSource;
import com.dabomstew.pkrandom.Randomizer;
import com.dabomstew.pkrandom.Settings;
import com.dabomstew.pkrandom.romhandlers.Gen1RomHandler;
import com.dabomstew.pkrandom.romhandlers.Gen2RomHandler;
import com.dabomstew.pkrandom.romhandlers.Gen3RomHandler;
import com.dabomstew.pkrandom.romhandlers.Gen4RomHandler;
import com.dabomstew.pkrandom.romhandlers.Gen5RomHandler;
import com.dabomstew.pkrandom.romhandlers.RomHandler;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ResourceBundle;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSObject;
import org.teavm.jso.typedarrays.Int8Array;
import org.teavm.runtime.fs.VirtualFileSystemProvider;

/**
 * JavaScript entry point. Mirrors CliRandomizer.performDirectRandomization,
 * but takes the ROM and settings as byte arrays instead of file paths.
 *
 * Results are fetched with the getters after {@link #randomize} returns,
 * because TeaVM exports are simplest with plain values.
 */
public final class WebRandomizer {

    private static final String WORK_DIR = "/work";

    private static WebFileSystem fs;

    private static byte[] outputData;
    private static int outputSize;
    private static String outputExtension = "";
    private static String romName = "";
    private static String log = "";
    private static String error = "";
    private static String warnings = "";

    private WebRandomizer() {
    }

    public static void main(String[] args) {
        fs();
    }

    private static WebFileSystem fs() {
        if (fs == null) {
            fs = new WebFileSystem();
            VirtualFileSystemProvider.setInstance(fs);
        }
        return fs;
    }

    /**
     * @param rom ROM bytes (view is used without copying)
     * @param romFileName original file name, only its extension matters
     * @param settingsFile contents of a .rnqs settings file
     * @param seed "" for a random seed, otherwise a decimal long
     * @return true on success; on failure see {@link #getError()}
     */
    @JSExport
    public static boolean randomize(Int8Array rom, String romFileName, Int8Array settingsFile, String seed) {
        WebFileSystem files = fs();
        files.clear();
        outputData = null;
        outputSize = 0;
        outputExtension = "";
        romName = "";
        log = "";
        error = "";
        StringBuilder warn = new StringBuilder();

        try {
            files.getFile("/").createDirectory("work");
            String settingsPath = WORK_DIR + "/settings.rnqs";
            byte[] settingsBytes = settingsFile.copyToJavaArray();
            files.mount(settingsPath, settingsBytes, settingsBytes.length);
            Settings settings;
            try (FileInputStream in = new FileInputStream(settingsPath)) {
                settings = Settings.read(in);
            }
            settings.setCustomNames(FileFunctions.getCustomNames());

            String ext = extensionOf(romFileName);
            String inputPath = WORK_DIR + "/input" + ext;
            byte[] romBytes = rom.toJavaArray();
            files.mount(inputPath, romBytes, romBytes.length);

            RomHandler.Factory[] factories = new RomHandler.Factory[] {
                    new Gen1RomHandler.Factory(),
                    new Gen2RomHandler.Factory(),
                    new Gen3RomHandler.Factory(),
                    new Gen4RomHandler.Factory(),
                    new Gen5RomHandler.Factory()
            };

            RomHandler handler = null;
            for (RomHandler.Factory factory : factories) {
                if (factory.isLoadable(inputPath)) {
                    handler = factory.create(RandomSource.instance());
                    handler.loadRom(inputPath);
                    break;
                }
            }
            if (handler == null) {
                error = "Diese ROM wird nicht unterstützt (nur Gen 1-5: GB, GBC, GBA, NDS).";
                return false;
            }

            romName = handler.getROMName();
            Settings.TweakForROMFeedback feedback = settings.tweakForRom(handler);
            if (feedback.isChangedStarter() && settings.getStartersMod() == Settings.StartersMod.CUSTOM) {
                warn.append("Die eingestellten Starter gibt es in diesem Spiel nicht und wurden angepasst.\n");
            }
            if (settings.isUpdatedFromOldVersion()) {
                warn.append("Die Settings-Datei stammt aus einer älteren Randomizer-Version.\n");
            }

            outputExtension = handler.getDefaultExtension();
            String outputPath = WORK_DIR + "/output." + outputExtension;
            files.reserve(outputPath, romBytes.length);

            ByteArrayOutputStream logBytes = new ByteArrayOutputStream();
            PrintStream logStream = new PrintStream(logBytes, false, "UTF-8");
            ResourceBundle bundle = com.dabomstew.pkrandom.WebBundle.get();
            Randomizer randomizer = new Randomizer(settings, handler, bundle, false);
            if (seed == null || seed.trim().isEmpty()) {
                randomizer.randomize(outputPath, logStream);
            } else {
                randomizer.randomize(outputPath, logStream, Long.parseLong(seed.trim()));
            }
            logStream.flush();
            log = new String(logBytes.toByteArray(), StandardCharsets.UTF_8);

            outputData = files.data(outputPath);
            outputSize = files.size(outputPath);
            if (outputData == null || outputSize <= 0) {
                error = "Der Randomizer hat keine ROM geschrieben.";
                return false;
            }
            return true;
        } catch (Throwable t) {
            StringWriter sw = new StringWriter();
            t.printStackTrace(new PrintWriter(sw));
            error = "Fehler beim Randomizen: " + t + "\n" + sw + "\nJS-Stack:\n" + jsStack((JSObject) (Object) t);
            return false;
        } finally {
            warnings = warn.toString();
            // Keep only the output buffer alive; drop input, temp files etc.
            byte[] keep = outputData;
            int keepSize = outputSize;
            files.clear();
            outputData = keep;
            outputSize = keepSize;
        }
    }

    /** View of the randomized ROM (no copy); valid until the next call. */
    @JSExport
    public static Int8Array getOutput() {
        if (outputData == null) {
            return null;
        }
        return Int8Array.fromJavaArray(outputData);
    }

    @JSExport
    public static int getOutputSize() {
        return outputSize;
    }

    @JSExport
    public static String getOutputExtension() {
        return outputExtension;
    }

    @JSExport
    public static String getRomName() {
        return romName;
    }

    @JSExport
    public static String getLog() {
        return log;
    }

    @JSExport
    public static String getError() {
        return error;
    }

    @JSExport
    public static String getWarnings() {
        return warnings;
    }

    /** Frees the output buffer once JavaScript has copied it out. */
    @JSExport
    public static void release() {
        outputData = null;
        outputSize = 0;
        log = "";
    }

    /** The JavaScript stack captured where the exception was thrown (function names = Java methods). */
    @JSBody(params = "t", script = "var e = t && t.$jsException; return e && e.stack ? String(e.stack) : '';")
    private static native String jsStack(JSObject t);

    private static String extensionOf(String name) {
        if (name == null) {
            return ".bin";
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return ".bin";
        }
        return name.substring(dot).toLowerCase();
    }
}

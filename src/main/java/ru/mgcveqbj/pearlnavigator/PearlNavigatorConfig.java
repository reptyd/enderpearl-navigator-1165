package ru.mgcveqbj.pearlnavigator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

final class PearlNavigatorConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    boolean enabled = true;
    boolean aimAssist = true;
    boolean showHud = true;
    boolean showWorldTarget = true;
    boolean showPrediction = true;
    boolean zoneMode = false;
    boolean targetEnabled = false;
    boolean audioCue = true;
    boolean nudgeEnabled = true;
    boolean insideBoost = true;
    boolean autoMode = false;
    boolean autoThrow = true;
    boolean autoSwitchPearl = true;
    boolean autoCrawl = true;
    boolean autoFullControl = true;
    boolean autoNudge = true;
    boolean autoMove = true;
    boolean autoRunup = false;
    boolean useMultiRay = true;

    double targetX = 0.0;
    double targetY = 64.0;
    double targetZ = 0.0;
    double tolerance = 0.6;

    float searchYawRange = 15.0f;
    float searchPitchRange = 30.0f;
    float searchStep = 0.5f;
    int searchIntervalTicks = 4;
    int predictionSteps = 160;

    float aimMaxDelta = 2.5f;

    float nudgeStrength = 0.2f;
    float nudgeVerticalStrength = 0.35f;

    int audioCooldownTicks = 20;
    int autoThrowCooldownTicks = 12;
    double autoThrowDistance = 3.0;
    double autoHiddenBonus = 1.0;
    double anchorTolerance = 0.35;
    float autoEdgeWeight = 1.25f;
    double autoMissPenaltyHidden = 6.0;
    double autoLowBias = 2.0;
    double autoWallBias = 1.0;
    double autoCloseWallDistance = 1.2;
    double autoCloseWallHighPenalty = 6.0;
    boolean autoCloseWallPitchLock = true;
    double autoWallStandDistance = 0.08;
    double autoWallStandTolerance = 0.06;
    double autoRunupDistance = 2.4;
    double autoRunupTolerance = 0.2;
    float autoMoveStrength = 0.9f;

    static PearlNavigatorConfig load(Path path) {
        if (!Files.exists(path)) {
            return new PearlNavigatorConfig();
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            PearlNavigatorConfig config = GSON.fromJson(reader, PearlNavigatorConfig.class);
            if (config == null) {
                return new PearlNavigatorConfig();
            }
            return config;
        } catch (IOException ignored) {
            return new PearlNavigatorConfig();
        }
    }

    static void save(Path path, PearlNavigatorConfig config) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException ignored) {
            // Best-effort config save.
        }
    }
}

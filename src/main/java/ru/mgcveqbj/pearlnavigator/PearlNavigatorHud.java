package ru.mgcveqbj.pearlnavigator;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.MathHelper;

final class PearlNavigatorHud {
    private PearlNavigatorHud() {
    }

    static void render(MatrixStack matrices, float tickDelta) {
        if (!PearlNavigator.shouldShowHud()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null) {
            return;
        }

        int x = 6;
        int y = 6;
        client.textRenderer.drawWithShadow(matrices, new LiteralText("Pearl Navigator"), x, y, 0xE6E6E6);
        y += 10;

        String status = PearlNavigator.config.enabled ? "ON" : "OFF";
        client.textRenderer.drawWithShadow(matrices, new LiteralText("Status: " + status), x, y, 0xB8B8B8);
        y += 10;

        String mode;
        if (PearlNavigator.isZoneMode()) {
            mode = "ZONE";
        } else if (PearlNavigator.config.autoMode) {
            mode = "AUTO";
        } else {
            mode = "ASSIST";
        }
        client.textRenderer.drawWithShadow(matrices, new LiteralText("Mode: " + mode), x, y, 0xB8B8B8);
        y += 10;

        if (PearlNavigator.hasTarget()) {
            client.textRenderer.drawWithShadow(matrices, new LiteralText(String.format(
                "Target: %.2f %.2f %.2f",
                PearlNavigator.config.targetX,
                PearlNavigator.config.targetY,
                PearlNavigator.config.targetZ
            )), x, y, 0xB8B8B8);
            y += 10;
        } else {
            client.textRenderer.drawWithShadow(matrices, new LiteralText("Target: OFF (set XYZ in config)"), x, y, 0xB8B8B8);
            y += 10;
        }

        if (PearlNavigator.shouldShowPrediction() && PearlNavigator.getCurrentPrediction() != null) {
            double distance = PearlNavigator.getCurrentDistance();
            double tol = PearlNavigator.getActiveAnchor() != null ? PearlNavigator.getAnchorTolerance() : PearlNavigator.getTolerance();
            client.textRenderer.drawWithShadow(matrices, new LiteralText(String.format(
                "Aim dist: %.2f (tol %.2f)",
                distance,
                tol
            )), x, y, 0xC6C6C6);
            y += 10;
        }

        PearlNavigator.AimSolution best = PearlNavigator.getBestAim();
        if (best != null) {
            client.textRenderer.drawWithShadow(matrices, new LiteralText(String.format(
                "Best: %.2f deg / %.2f deg d=%.2f",
                MathHelper.wrapDegrees(best.yaw),
                MathHelper.clamp(best.pitch, -90.0f, 90.0f),
                best.distance
            )), x, y, 0xC6C6C6);
        }
    }
}

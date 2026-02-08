package ru.mgcveqbj.pearlnavigator;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

final class PearlNavigatorConfigScreen extends Screen {
    private final Screen parent;

    private TextFieldWidget xField;
    private TextFieldWidget yField;
    private TextFieldWidget zField;
    private TextFieldWidget toleranceField;
    private TextFieldWidget yawRangeField;
    private TextFieldWidget pitchRangeField;
    private TextFieldWidget stepField;
    private TextFieldWidget aimDeltaField;
    private TextFieldWidget nudgeField;
    private TextFieldWidget nudgeVerticalField;
    private ButtonWidget targetButton;
    private ButtonWidget autoMoveButton;
    private ButtonWidget autoRunupButton;
    private ButtonWidget insideBoostButton;
    private ButtonWidget zoneModeButton;
    private ButtonWidget multiRayButton;
    private SliderWidget intervalSlider;
    private SliderWidget stepsSlider;
    private SliderWidget moveStrengthSlider;
    private SliderWidget runupSlider;
    private SliderWidget standSlider;
    private SliderWidget anchorTolSlider;
    private int leftX;
    private int rightX;
    private int baseY;

    PearlNavigatorConfigScreen(Screen parent) {
        super(new LiteralText("Pearl Navigator Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = 28;
        int fieldWidth = 100;
        int fieldHeight = 18;
        leftX = centerX - 110;
        rightX = centerX + 20;
        baseY = y;

        xField = new TextFieldWidget(this.textRenderer, leftX, y, fieldWidth, fieldHeight, new LiteralText("X"));
        xField.setText(String.valueOf(PearlNavigator.config.targetX));
        this.children.add(xField);

        yField = new TextFieldWidget(this.textRenderer, leftX, y + 22, fieldWidth, fieldHeight, new LiteralText("Y"));
        yField.setText(String.valueOf(PearlNavigator.config.targetY));
        this.children.add(yField);

        zField = new TextFieldWidget(this.textRenderer, leftX, y + 44, fieldWidth, fieldHeight, new LiteralText("Z"));
        zField.setText(String.valueOf(PearlNavigator.config.targetZ));
        this.children.add(zField);

        toleranceField = new TextFieldWidget(this.textRenderer, rightX, y, fieldWidth, fieldHeight, new LiteralText("Tolerance"));
        toleranceField.setText(String.valueOf(PearlNavigator.config.tolerance));
        this.children.add(toleranceField);

        yawRangeField = new TextFieldWidget(this.textRenderer, rightX, y + 22, fieldWidth, fieldHeight, new LiteralText("Yaw range"));
        yawRangeField.setText(String.valueOf(PearlNavigator.config.searchYawRange));
        this.children.add(yawRangeField);

        pitchRangeField = new TextFieldWidget(this.textRenderer, rightX, y + 44, fieldWidth, fieldHeight, new LiteralText("Pitch range"));
        pitchRangeField.setText(String.valueOf(PearlNavigator.config.searchPitchRange));
        this.children.add(pitchRangeField);

        stepField = new TextFieldWidget(this.textRenderer, rightX, y + 66, fieldWidth, fieldHeight, new LiteralText("Search step"));
        stepField.setText(String.valueOf(PearlNavigator.config.searchStep));
        this.children.add(stepField);

        aimDeltaField = new TextFieldWidget(this.textRenderer, leftX, y + 66, fieldWidth, fieldHeight, new LiteralText("Aim delta"));
        aimDeltaField.setText(String.valueOf(PearlNavigator.config.aimMaxDelta));
        this.children.add(aimDeltaField);

        nudgeField = new TextFieldWidget(this.textRenderer, leftX, y + 88, fieldWidth, fieldHeight, new LiteralText("Nudge"));
        nudgeField.setText(String.valueOf(PearlNavigator.config.nudgeStrength));
        this.children.add(nudgeField);

        nudgeVerticalField = new TextFieldWidget(this.textRenderer, rightX, y + 88, fieldWidth, fieldHeight, new LiteralText("Nudge Y"));
        nudgeVerticalField.setText(String.valueOf(PearlNavigator.config.nudgeVerticalStrength));
        this.children.add(nudgeVerticalField);

        int buttonY = y + 110;
        this.addButton(new ButtonWidget(leftX, buttonY, 100, 20, toggleLabel("Auto mode", PearlNavigator.config.autoMode), button -> {
            PearlNavigator.config.autoMode = !PearlNavigator.config.autoMode;
            button.setMessage(toggleLabel("Auto mode", PearlNavigator.config.autoMode));
        }));

        this.addButton(new ButtonWidget(rightX, buttonY, 100, 20, toggleLabel("Full control", PearlNavigator.config.autoFullControl), button -> {
            PearlNavigator.config.autoFullControl = !PearlNavigator.config.autoFullControl;
            button.setMessage(toggleLabel("Full control", PearlNavigator.config.autoFullControl));
        }));

        buttonY += 24;
        this.addButton(new ButtonWidget(leftX, buttonY, 100, 20, toggleLabel("Auto throw", PearlNavigator.config.autoThrow), button -> {
            PearlNavigator.config.autoThrow = !PearlNavigator.config.autoThrow;
            button.setMessage(toggleLabel("Auto throw", PearlNavigator.config.autoThrow));
        }));

        this.addButton(new ButtonWidget(rightX, buttonY, 100, 20, toggleLabel("Auto switch", PearlNavigator.config.autoSwitchPearl), button -> {
            PearlNavigator.config.autoSwitchPearl = !PearlNavigator.config.autoSwitchPearl;
            button.setMessage(toggleLabel("Auto switch", PearlNavigator.config.autoSwitchPearl));
        }));

        buttonY += 24;
        this.addButton(new ButtonWidget(leftX, buttonY, 100, 20, toggleLabel("Auto crawl", PearlNavigator.config.autoCrawl), button -> {
            PearlNavigator.config.autoCrawl = !PearlNavigator.config.autoCrawl;
            button.setMessage(toggleLabel("Auto crawl", PearlNavigator.config.autoCrawl));
        }));

        this.addButton(new ButtonWidget(rightX, buttonY, 100, 20, toggleLabel("Auto nudge", PearlNavigator.config.autoNudge), button -> {
            PearlNavigator.config.autoNudge = !PearlNavigator.config.autoNudge;
            button.setMessage(toggleLabel("Auto nudge", PearlNavigator.config.autoNudge));
        }));

        buttonY += 24;
        autoMoveButton = this.addButton(new ButtonWidget(leftX, buttonY, 100, 20, toggleLabel("Auto move", PearlNavigator.config.autoMove), button -> {
            PearlNavigator.config.autoMove = !PearlNavigator.config.autoMove;
            button.setMessage(toggleLabel("Auto move", PearlNavigator.config.autoMove));
        }));

        autoRunupButton = this.addButton(new ButtonWidget(rightX, buttonY, 100, 20, toggleLabel("Run-up", PearlNavigator.config.autoRunup), button -> {
            PearlNavigator.config.autoRunup = !PearlNavigator.config.autoRunup;
            button.setMessage(toggleLabel("Run-up", PearlNavigator.config.autoRunup));
        }));

        buttonY += 24;
        insideBoostButton = this.addButton(new ButtonWidget(leftX, buttonY, 100, 20, toggleLabel("Inside boost", PearlNavigator.config.insideBoost), button -> {
            PearlNavigator.config.insideBoost = !PearlNavigator.config.insideBoost;
            button.setMessage(toggleLabel("Inside boost", PearlNavigator.config.insideBoost));
        }));

        zoneModeButton = this.addButton(new ButtonWidget(rightX, buttonY, 100, 20, toggleLabel("Zone mode", PearlNavigator.config.zoneMode), button -> {
            PearlNavigator.config.zoneMode = !PearlNavigator.config.zoneMode;
            button.setMessage(toggleLabel("Zone mode", PearlNavigator.config.zoneMode));
        }));

        buttonY += 24;
        this.addButton(new ButtonWidget(leftX, buttonY, 100, 20, toggleLabel("Mod", PearlNavigator.config.enabled), button -> {
            PearlNavigator.config.enabled = !PearlNavigator.config.enabled;
            button.setMessage(toggleLabel("Mod", PearlNavigator.config.enabled));
        }));

        targetButton = this.addButton(new ButtonWidget(rightX, buttonY, 100, 20, toggleLabel("Target", PearlNavigator.config.targetEnabled), button -> {
            PearlNavigator.config.targetEnabled = !PearlNavigator.config.targetEnabled;
            button.setMessage(toggleLabel("Target", PearlNavigator.config.targetEnabled));
        }));

        buttonY += 24;
        this.addButton(new ButtonWidget(leftX, buttonY, 100, 20, toggleLabel("Aim assist", PearlNavigator.config.aimAssist), button -> {
            PearlNavigator.config.aimAssist = !PearlNavigator.config.aimAssist;
            button.setMessage(toggleLabel("Aim assist", PearlNavigator.config.aimAssist));
        }));

        this.addButton(new ButtonWidget(rightX, buttonY, 100, 20, toggleLabel("Nudge", PearlNavigator.config.nudgeEnabled), button -> {
            PearlNavigator.config.nudgeEnabled = !PearlNavigator.config.nudgeEnabled;
            button.setMessage(toggleLabel("Nudge", PearlNavigator.config.nudgeEnabled));
        }));

        buttonY += 24;
        this.addButton(new ButtonWidget(leftX, buttonY, 100, 20, toggleLabel("HUD", PearlNavigator.config.showHud), button -> {
            PearlNavigator.config.showHud = !PearlNavigator.config.showHud;
            button.setMessage(toggleLabel("HUD", PearlNavigator.config.showHud));
        }));

        this.addButton(new ButtonWidget(rightX, buttonY, 100, 20, toggleLabel("Audio", PearlNavigator.config.audioCue), button -> {
            PearlNavigator.config.audioCue = !PearlNavigator.config.audioCue;
            button.setMessage(toggleLabel("Audio", PearlNavigator.config.audioCue));
        }));

        buttonY += 24;
        this.addButton(new ButtonWidget(leftX, buttonY, 100, 20, toggleLabel("World box", PearlNavigator.config.showWorldTarget), button -> {
            PearlNavigator.config.showWorldTarget = !PearlNavigator.config.showWorldTarget;
            button.setMessage(toggleLabel("World box", PearlNavigator.config.showWorldTarget));
        }));

        this.addButton(new ButtonWidget(rightX, buttonY, 100, 20, new LiteralText("Set crosshair"), button -> setTargetFromCrosshair()));

        buttonY += 24;
        this.addButton(new ButtonWidget(leftX, buttonY, 100, 20, new LiteralText("Use player pos"), button -> setTargetFromPlayer()));

        multiRayButton = this.addButton(new ButtonWidget(rightX, buttonY, 100, 20, toggleLabel("Multi-ray", PearlNavigator.config.useMultiRay), button -> {
            PearlNavigator.config.useMultiRay = !PearlNavigator.config.useMultiRay;
            button.setMessage(toggleLabel("Multi-ray", PearlNavigator.config.useMultiRay));
        }));

        buttonY += 26;
        intervalSlider = this.addButton(new ConfigSlider(leftX, buttonY, 100, 20,
            "Search tick", 1.0, 12.0, () -> PearlNavigator.config.searchIntervalTicks,
            value -> PearlNavigator.config.searchIntervalTicks = (int) Math.round(value), 0));

        stepsSlider = this.addButton(new ConfigSlider(rightX, buttonY, 100, 20,
            "Pred steps", 40.0, 220.0, () -> PearlNavigator.config.predictionSteps,
            value -> PearlNavigator.config.predictionSteps = (int) Math.round(value), 0));

        buttonY += 24;
        moveStrengthSlider = this.addButton(new ConfigSlider(leftX, buttonY, 100, 20,
            "Move str", 0.2, 1.0, () -> PearlNavigator.config.autoMoveStrength,
            value -> PearlNavigator.config.autoMoveStrength = (float) value, 2));

        runupSlider = this.addButton(new ConfigSlider(rightX, buttonY, 100, 20,
            "Run-up dist", 0.6, 4.0, () -> PearlNavigator.config.autoRunupDistance,
            value -> PearlNavigator.config.autoRunupDistance = value, 2));

        buttonY += 24;
        standSlider = this.addButton(new ConfigSlider(leftX, buttonY, 100, 20,
            "Wall dist", 0.02, 0.3, () -> PearlNavigator.config.autoWallStandDistance,
            value -> PearlNavigator.config.autoWallStandDistance = value, 2));

        buttonY += 24;
        anchorTolSlider = this.addButton(new ConfigSlider(rightX, buttonY, 100, 20,
            "Anchor tol", 0.1, 0.8, () -> PearlNavigator.config.anchorTolerance,
            value -> PearlNavigator.config.anchorTolerance = value, 2));

        buttonY += 24;
        this.addButton(new ButtonWidget(centerX - 60, buttonY, 120, 20, new LiteralText("Save and close"), button -> closeScreen()));
    }

    @Override
    public void tick() {
        xField.tick();
        yField.tick();
        zField.tick();
        toleranceField.tick();
        yawRangeField.tick();
        pitchRangeField.tick();
        stepField.tick();
        aimDeltaField.tick();
        nudgeField.tick();
        nudgeVerticalField.tick();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (xField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (yField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (zField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (toleranceField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (yawRangeField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (pitchRangeField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (stepField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (aimDeltaField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (nudgeField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (nudgeVerticalField.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (xField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (yField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (zField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (toleranceField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (yawRangeField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (pitchRangeField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (stepField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (aimDeltaField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (nudgeField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (nudgeVerticalField.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (xField.charTyped(chr, modifiers)) {
            return true;
        }
        if (yField.charTyped(chr, modifiers)) {
            return true;
        }
        if (zField.charTyped(chr, modifiers)) {
            return true;
        }
        if (toleranceField.charTyped(chr, modifiers)) {
            return true;
        }
        if (yawRangeField.charTyped(chr, modifiers)) {
            return true;
        }
        if (pitchRangeField.charTyped(chr, modifiers)) {
            return true;
        }
        if (stepField.charTyped(chr, modifiers)) {
            return true;
        }
        if (aimDeltaField.charTyped(chr, modifiers)) {
            return true;
        }
        if (nudgeField.charTyped(chr, modifiers)) {
            return true;
        }
        if (nudgeVerticalField.charTyped(chr, modifiers)) {
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public void onClose() {
        closeScreen();
    }

    private void closeScreen() {
        applyValues();
        PearlNavigator.saveConfig();
        if (client != null) {
            client.openScreen(parent);
        }
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        this.renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);
        drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, 10, 0xE6E6E6);

        int labelX = leftX - 18;
        this.textRenderer.drawWithShadow(matrices, new LiteralText("Target position (player landing)"), leftX, baseY - 12, 0xB8B8B8);
        this.textRenderer.drawWithShadow(matrices, new LiteralText("X"), labelX, baseY + 4, 0x9FA3A8);
        this.textRenderer.drawWithShadow(matrices, new LiteralText("Y"), labelX, baseY + 26, 0x9FA3A8);
        this.textRenderer.drawWithShadow(matrices, new LiteralText("Z"), labelX, baseY + 48, 0x9FA3A8);
        this.textRenderer.drawWithShadow(matrices, new LiteralText("Tolerance (blocks)"), rightX, baseY - 12, 0xB8B8B8);
        this.textRenderer.drawWithShadow(matrices, new LiteralText("Search (deg)"), rightX, baseY + 36, 0xB8B8B8);
        this.textRenderer.drawWithShadow(matrices, new LiteralText("Aim smooth"), leftX, baseY + 54, 0xB8B8B8);
        this.textRenderer.drawWithShadow(matrices, new LiteralText("Nudge strength"), leftX, baseY + 76, 0xB8B8B8);
        this.textRenderer.drawWithShadow(matrices, new LiteralText("Nudge vertical"), rightX, baseY + 76, 0xB8B8B8);
        this.textRenderer.drawWithShadow(matrices, new LiteralText("Auto mode takes full control"), leftX, baseY + 106, 0x8A8E93);
        this.textRenderer.drawWithShadow(matrices, new LiteralText("Set target via XYZ or buttons"), leftX, baseY + 116, 0x8A8E93);
        this.textRenderer.drawWithShadow(matrices, new LiteralText("Perf: bigger step/range = faster, less accurate"), leftX, baseY + 126, 0x8A8E93);

        xField.render(matrices, mouseX, mouseY, delta);
        yField.render(matrices, mouseX, mouseY, delta);
        zField.render(matrices, mouseX, mouseY, delta);
        toleranceField.render(matrices, mouseX, mouseY, delta);
        yawRangeField.render(matrices, mouseX, mouseY, delta);
        pitchRangeField.render(matrices, mouseX, mouseY, delta);
        stepField.render(matrices, mouseX, mouseY, delta);
        aimDeltaField.render(matrices, mouseX, mouseY, delta);
        nudgeField.render(matrices, mouseX, mouseY, delta);
        nudgeVerticalField.render(matrices, mouseX, mouseY, delta);
    }

    private void applyValues() {
        double nextX = parseDouble(xField.getText(), PearlNavigator.config.targetX);
        double nextY = parseDouble(yField.getText(), PearlNavigator.config.targetY);
        double nextZ = parseDouble(zField.getText(), PearlNavigator.config.targetZ);
        boolean targetChanged = Math.abs(nextX - PearlNavigator.config.targetX) > 1.0e-6
            || Math.abs(nextY - PearlNavigator.config.targetY) > 1.0e-6
            || Math.abs(nextZ - PearlNavigator.config.targetZ) > 1.0e-6;
        PearlNavigator.config.targetX = nextX;
        PearlNavigator.config.targetY = nextY;
        PearlNavigator.config.targetZ = nextZ;
        if (targetChanged) {
            PearlNavigator.config.targetEnabled = true;
        }
        PearlNavigator.config.tolerance = clamp(parseDouble(toleranceField.getText(), PearlNavigator.config.tolerance), 0.1, 5.0);
        PearlNavigator.config.searchYawRange = (float) clamp(parseDouble(yawRangeField.getText(), PearlNavigator.config.searchYawRange), 1.0, 90.0);
        PearlNavigator.config.searchPitchRange = (float) clamp(parseDouble(pitchRangeField.getText(), PearlNavigator.config.searchPitchRange), 1.0, 90.0);
        PearlNavigator.config.searchStep = (float) clamp(parseDouble(stepField.getText(), PearlNavigator.config.searchStep), 0.05, 5.0);
        PearlNavigator.config.aimMaxDelta = (float) clamp(parseDouble(aimDeltaField.getText(), PearlNavigator.config.aimMaxDelta), 0.1, 15.0);
        PearlNavigator.config.nudgeStrength = (float) clamp(parseDouble(nudgeField.getText(), PearlNavigator.config.nudgeStrength), 0.01, 1.0);
        PearlNavigator.config.nudgeVerticalStrength = (float) clamp(parseDouble(nudgeVerticalField.getText(), PearlNavigator.config.nudgeVerticalStrength), 0.01, 1.0);
    }

    private void setTargetFromCrosshair() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.crosshairTarget == null) {
            return;
        }
        HitResult hit = client.crosshairTarget;
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult) {
            BlockHitResult blockHit = (BlockHitResult) hit;
            BlockPos pos = blockHit.getBlockPos();
            Vec3d target = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            setTarget(target);
        }
    }

    private void setTargetFromPlayer() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.player == null) {
            return;
        }
        Vec3d target = client.player.getPos();
        setTarget(target);
    }

    private void setTarget(Vec3d target) {
        xField.setText(String.format(Locale.ROOT, "%.3f", target.x));
        yField.setText(String.format(Locale.ROOT, "%.3f", target.y));
        zField.setText(String.format(Locale.ROOT, "%.3f", target.z));
        PearlNavigator.config.targetEnabled = true;
        if (targetButton != null) {
            targetButton.setMessage(toggleLabel("Target", true));
        }
    }

    private LiteralText toggleLabel(String label, boolean state) {
        return new LiteralText(label + ": " + (state ? "ON" : "OFF"));
    }

    private static double parseDouble(String text, double fallback) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }

    private static final class ConfigSlider extends SliderWidget {
        private final String label;
        private final double min;
        private final double max;
        private final DoubleSupplier getter;
        private final DoubleConsumer setter;
        private final int decimals;

        private ConfigSlider(int x, int y, int width, int height, String label,
                             double min, double max, DoubleSupplier getter, DoubleConsumer setter, int decimals) {
            super(x, y, width, height, new LiteralText(""), 0.0);
            this.label = label;
            this.min = min;
            this.max = max;
            this.getter = getter;
            this.setter = setter;
            this.decimals = decimals;
            double value = getter.getAsDouble();
            this.value = (value - min) / (max - min);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(new LiteralText(label + ": " + formatValue()));
        }

        @Override
        protected void applyValue() {
            double raw = min + (max - min) * value;
            setter.accept(raw);
        }

        private String formatValue() {
            double raw = min + (max - min) * value;
            String pattern = "%." + decimals + "f";
            return String.format(Locale.ROOT, pattern, raw);
        }
    }
}

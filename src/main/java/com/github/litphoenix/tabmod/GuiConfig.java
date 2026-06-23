package com.github.litphoenix.tabmod;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GuiConfig extends GuiScreen {

    private final List<String> gamesList = new ArrayList<>();
    private int scrollOffset = 0;

    @Override
    public void initGui() {
        gamesList.clear();
        gamesList.addAll(ExampleMod.thresholds.keySet());
        updateButtons();
    }

    private void updateButtons() {
        this.buttonList.clear();
        
        // Navigation buttons
        this.buttonList.add(new GuiButton(900, this.width / 2 - 155, 35, 20, 20, "^"));
        this.buttonList.add(new GuiButton(901, this.width / 2 - 155, 165, 20, 20, "v"));

        int yPos = 60;
        for (int i = scrollOffset; i < Math.min(scrollOffset + 5, gamesList.size()); i++) {
            String game = gamesList.get(i);
            double[] limits = ExampleMod.thresholds.get(game);

            // IDs mapping: index * 10 + action
            this.buttonList.add(new GuiButton(i * 10, this.width / 2 + 10, yPos, 30, 20, "-0.1"));
            this.buttonList.add(new GuiButton(i * 10 + 1, this.width / 2 + 42, yPos, 30, 20, "+0.1"));
            this.buttonList.add(new GuiButton(i * 10 + 2, this.width / 2 + 82, yPos, 30, 20, "-0.1"));
            this.buttonList.add(new GuiButton(i * 10 + 3, this.width / 2 + 114, yPos, 30, 20, "+0.1"));
            
            yPos += 24;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 900 && scrollOffset > 0) {
            scrollOffset--;
            updateButtons();
            return;
        }
        if (button.id == 901 && scrollOffset < gamesList.size() - 5) {
            scrollOffset++;
            updateButtons();
            return;
        }

        if (button.id < 900) {
            int index = button.id / 10;
            int type = button.id % 10;
            String game = gamesList.get(index);
            double[] vals = ExampleMod.thresholds.get(game);

            if (type == 0) vals[0] = Math.max(0.1, vals[0] - 0.1);
            if (type == 1) vals[0] = Math.min(5.0, vals[0] + 0.1);
            if (type == 2) vals[1] = Math.max(0.1, vals[1] - 0.1);
            if (type == 3) vals[1] = Math.min(10.0, vals[1] + 0.1);

            ExampleMod.thresholds.put(game, vals);
            ExampleMod.saveThresholds();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        
        String detected = ExampleMod.detectGameCached();
        if (detected == null) detected = "None / Lobby";

        this.drawCenteredString(this.fontRendererObj, "\u00A7nTabMod Settings Menu", this.width / 2, 12, 0xFFFFFF);
        this.drawString(this.fontRendererObj, "\u00A7eGAME DETECTED: \u00A7a" + detected, this.width / 2 - 120, 38, 0xFFFFFF);

        int yPos = 66;
        for (int i = scrollOffset; i < Math.min(scrollOffset + 5, gamesList.size()); i++) {
            String game = gamesList.get(i);
            double[] limits = ExampleMod.thresholds.get(game);

            String activeIndicator = game.equalsIgnoreCase(detected) ? "\u00A7a> " : "\u00A77";
            this.drawString(this.fontRendererObj, activeIndicator + game, this.width / 2 - 130, yPos, 0xFFFFFF);
            this.drawString(this.fontRendererObj, String.format("\u00A7eSwt: \u00A7c%.1f", limits[0]), this.width / 2 - 40, yPos, 0xFFFFFF);
            this.drawString(this.fontRendererObj, String.format("\u00A7eExt: \u00A74%.1f", limits[1]), this.width / 2 - 2, yPos, 0xFFFFFF);

            yPos += 24;
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}

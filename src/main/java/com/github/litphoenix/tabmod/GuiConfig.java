package com.github.litphoenix.tabmod;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GuiConfig extends GuiScreen {

    private final List<String> gamesList = new ArrayList<>();
    private int scrollOffset = 0;
    private GuiTextField newGameField;

    @Override
    public void initGui() {
        gamesList.clear();
        gamesList.addAll(ExampleMod.gameProfiles.keySet());
        updateButtons();
        
        newGameField = new GuiTextField(0, this.fontRendererObj, this.width / 2 - 100, this.height - 30, 100, 20);
        newGameField.setMaxStringLength(30);
    }

    private void updateButtons() {
        this.buttonList.clear();
        
        this.buttonList.add(new GuiButton(900, this.width / 2 - 155, 35, 20, 20, "^"));
        this.buttonList.add(new GuiButton(901, this.width / 2 - 155, 165, 20, 20, "v"));
        this.buttonList.add(new GuiButton(902, this.width / 2 + 5, this.height - 30, 95, 20, "Add Game"));

        int yPos = 60;
        for (int i = scrollOffset; i < Math.min(scrollOffset + 5, gamesList.size()); i++) {
            String game = gamesList.get(i);
            boolean enabled = ExampleMod.gameProfiles.get(game).enabled;
            
            this.buttonList.add(new GuiButton(i * 10, this.width / 2 + 10, yPos, 50, 20, enabled ? "\u00A7aON" : "\u00A7cOFF"));
            this.buttonList.add(new GuiButton(i * 10 + 1, this.width / 2 + 65, yPos, 50, 20, "Edit"));
            
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
        if (button.id == 902) {
            String newGame = newGameField.getText().trim();
            if (!newGame.isEmpty() && !ExampleMod.gameProfiles.containsKey(newGame)) {
                ExampleMod.gameProfiles.put(newGame, new ExampleMod.GameProfile());
                ExampleMod.saveProfiles();
                initGui(); 
            }
            return;
        }

        if (button.id < 900) {
            int index = button.id / 10;
            int action = button.id % 10;
            String game = gamesList.get(index);
            
            if (action == 0) {
                ExampleMod.GameProfile profile = ExampleMod.gameProfiles.get(game);
                profile.enabled = !profile.enabled;
                ExampleMod.saveProfiles();
                updateButtons();
            } else if (action == 1) {
                this.mc.displayGuiScreen(new GuiEditGame(this, game));
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (newGameField.textboxKeyTyped(typedChar, keyCode)) return;
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        newGameField.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        String detected = ExampleMod.detectGameCached();
        if (detected == null) detected = "None / Lobby";

        this.drawCenteredString(this.fontRendererObj, "\u00A7nProfiles Menu", this.width / 2, 12, 0xFFFFFF);
        this.drawString(this.fontRendererObj, "\u00A7eDETECTED: \u00A7a" + detected, this.width / 2 - 120, 38, 0xFFFFFF);

        int yPos = 66;
        for (int i = scrollOffset; i < Math.min(scrollOffset + 5, gamesList.size()); i++) {
            String game = gamesList.get(i);
            String activeIndicator = game.equalsIgnoreCase(detected) ? "\u00A7a> " : "\u00A77";
            this.drawString(this.fontRendererObj, activeIndicator + game, this.width / 2 - 130, yPos, 0xFFFFFF);
            yPos += 24;
        }

        newGameField.drawTextBox();
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}

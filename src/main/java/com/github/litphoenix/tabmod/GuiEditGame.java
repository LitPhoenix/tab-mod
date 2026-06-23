package com.github.litphoenix.tabmod;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import java.io.IOException;

public class GuiEditGame extends GuiScreen {

    private final GuiScreen parent;
    private final String gameName;
    private final ExampleMod.GameProfile profile;

    private GuiTextField stat1Field, stat2Field, prefixField, suffixField, decField;
    
    public GuiEditGame(GuiScreen parent, String gameName) {
        this.parent = parent;
        this.gameName = gameName;
        this.profile = ExampleMod.gameProfiles.get(gameName);
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.buttonList.add(new GuiButton(0, this.width / 2 - 100, this.height - 30, 95, 20, "Save"));
        this.buttonList.add(new GuiButton(1, this.width / 2 + 5, this.height - 30, 95, 20, "Cancel"));

        // Adjustors for thresholds
        this.buttonList.add(new GuiButton(10, this.width / 2 + 10, 140, 30, 20, "-0.1"));
        this.buttonList.add(new GuiButton(11, this.width / 2 + 42, 140, 30, 20, "+0.1"));
        this.buttonList.add(new GuiButton(12, this.width / 2 + 10, 165, 30, 20, "-0.1"));
        this.buttonList.add(new GuiButton(13, this.width / 2 + 42, 165, 30, 20, "+0.1"));

        stat1Field = createField(0, 40, profile.stat1, 100);
        stat2Field = createField(1, 65, profile.stat2, 100);
        prefixField = createField(2, 90, profile.prefix, 30);
        suffixField = createField(3, 115, profile.suffix, 30);
        decField = createField(4, 90, String.valueOf(profile.decimals), 20);
        
        // Reposition decimals
        decField.xPosition = this.width / 2 + 50;
    }

    private GuiTextField createField(int id, int y, String text, int width) {
        GuiTextField field = new GuiTextField(id, this.fontRendererObj, this.width / 2 - 50, y, width, 15);
        field.setMaxStringLength(50);
        field.setText(text);
        return field;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            profile.stat1 = stat1Field.getText().trim();
            profile.stat2 = stat2Field.getText().trim();
            profile.prefix = prefixField.getText().trim();
            profile.suffix = suffixField.getText().trim();
            try { profile.decimals = Integer.parseInt(decField.getText().trim()); } catch (Exception e) {}
            
            ExampleMod.saveProfiles();
            this.mc.displayGuiScreen(parent);
        }
        if (button.id == 1) this.mc.displayGuiScreen(parent);
        
        if (button.id == 10) profile.swt = Math.max(0.1, profile.swt - 0.1);
        if (button.id == 11) profile.swt = Math.min(5.0, profile.swt + 0.1);
        if (button.id == 12) profile.ext = Math.max(0.1, profile.ext - 0.1);
        if (button.id == 13) profile.ext = Math.min(10.0, profile.ext + 0.1);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (stat1Field.textboxKeyTyped(typedChar, keyCode)) return;
        if (stat2Field.textboxKeyTyped(typedChar, keyCode)) return;
        if (prefixField.textboxKeyTyped(typedChar, keyCode)) return;
        if (suffixField.textboxKeyTyped(typedChar, keyCode)) return;
        if (decField.textboxKeyTyped(typedChar, keyCode)) return;
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        stat1Field.mouseClicked(mouseX, mouseY, mouseButton);
        stat2Field.mouseClicked(mouseX, mouseY, mouseButton);
        prefixField.mouseClicked(mouseX, mouseY, mouseButton);
        suffixField.mouseClicked(mouseX, mouseY, mouseButton);
        decField.mouseClicked(mouseX, mouseY, mouseButton);
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        this.drawCenteredString(this.fontRendererObj, "\u00A7nEditing: " + gameName, this.width / 2, 15, 0xFFFFFF);
        
        this.drawString(this.fontRendererObj, "API Key 1 (Wins/Kills):", this.width / 2 - 160, 43, 0xFFFFFF);
        this.drawString(this.fontRendererObj, "API Key 2 (Losses/Deaths):", this.width / 2 - 160, 68, 0xFFFFFF);
        this.drawString(this.fontRendererObj, "Prefix:", this.width / 2 - 160, 93, 0xFFFFFF);
        this.drawString(this.fontRendererObj, "Suffix:", this.width / 2 - 160, 118, 0xFFFFFF);
        this.drawString(this.fontRendererObj, "Decimals:", this.width / 2, 93, 0xFFFFFF);
        
        this.drawString(this.fontRendererObj, String.format("Sweat: %.1f", profile.swt), this.width / 2 - 80, 146, 0xFFFFFF);
        this.drawString(this.fontRendererObj, String.format("Extreme: %.1f", profile.ext), this.width / 2 - 80, 171, 0xFFFFFF);

        stat1Field.drawTextBox();
        stat2Field.drawTextBox();
        prefixField.drawTextBox();
        suffixField.drawTextBox();
        decField.drawTextBox();
        
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
}

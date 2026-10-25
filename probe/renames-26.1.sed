# Mechanical 1.21.11 -> 26.1 renames, applied to a throwaway copy to see the errors that remain after them.
s/\bGuiGraphics\b/GuiGraphicsExtractor/g
s/rendering\.v1\.world\.WorldRenderEvents/rendering.v1.level.LevelRenderEvents/
s/rendering\.v1\.world\.WorldRenderContext/rendering.v1.level.LevelRenderContext/
s/\bWorldRenderEvents\b/LevelRenderEvents/g
s/\bWorldRenderContext\b/LevelRenderContext/g
s/LevelRenderEvents\.BEFORE_ENTITIES/LevelRenderEvents.COLLECT_SUBMITS/g
s/context\.matrices()/context.poseStack()/g
s/context\.commandQueue()/context.submitNodeCollector()/g
s/keybinding\.v1\.KeyBindingHelper/keymapping.v1.KeyMappingHelper/
s/\bKeyBindingHelper\.registerKeyBinding\b/KeyMappingHelper.registerKeyMapping/g
s/net\.minecraft\.client\.renderer\.LightTexture;/net.minecraft.util.LightCoordsUtil;/
s/\bLightTexture\b/LightCoordsUtil/g
s/\.resizeDisplay()/.resizeGui()/g
s/public void renderBackground(GuiGraphicsExtractor/public void extractBackground(GuiGraphicsExtractor/
s/super\.renderBackground(/super.extractBackground(/
s/public void render(GuiGraphicsExtractor g, int mouseX/public void extractRenderState(GuiGraphicsExtractor g, int mouseX/
s/\.drawString(/.text(/g
s/\.drawCenteredString(/.centeredText(/g

# Extra mechanical renames for 26.2 and later on top of renames-26.1.sed:
# Minecraft.screen/setScreen moved to Minecraft.gui (now the screen manager); the old Gui is Minecraft.gui.hud.
s/\.getMainCamera()/.mainCamera()/g
s/I18n\.exists(/net.minecraft.locale.Language.getInstance().has(/g
s/\b\(client\|mc\|minecraft\)\.screen\b/\1.gui.screen()/g
s/\b\(client\|mc\|minecraft\)\.setScreen(/\1.gui.setScreen(/g
s/\.gui\.setOverlayMessage(/.gui.hud.setOverlayMessage(/g
s/\.gui\.getBossOverlay()/.gui.hud.getBossOverlay()/g
s/client\.options\.hideGui = arg\.equals("off");/{ if (client.gui.hud.isHidden() != arg.equals("off")) client.gui.hud.toggle(); }/
s/\.options\.hideGui\b/.gui.hud.isHidden()/g
s/client\.getMainRenderTarget()/client.gameRenderer.mainRenderTarget()/g

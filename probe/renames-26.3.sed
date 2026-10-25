# Extra mechanical renames for 26.3 on top of renames-26.1.sed (blaze3d -> renderpearl, renamed getters).
s/com\.mojang\.blaze3d\.textures\./com.mojang.renderpearl.api.textures./g
s/com\.mojang\.blaze3d\.buffers\./com.mojang.renderpearl.api.buffers./g
s/com\.mojang\.blaze3d\.pipeline\./com.mojang.renderpearl.api.pipeline./g
s/\.getMainCamera()/.mainCamera()/g
s/InputConstants\.Type\.KEYSYM/InputConstants.Type.KEYBOARD/g
s/pose\.mulPose(orientation)/pose.rotate(orientation)/
s/confirmLinkNow(this, \(GITHUB\|LRCLIB\))/confirmLinkNow(this, java.net.URI.create(\1))/
/^import org\.lwjgl\.glfw\.GLFW;/d
s/GLFW\.GLFW_KEY_\([A-Z0-9_]*\)/com.mojang.blaze3d.platform.InputConstants.KEY_\1/g
s/I18n\.exists(/net.minecraft.locale.Language.getInstance().has(/g
s/\b\(client\|mc\|minecraft\)\.screen\b/\1.gui.screen()/g
s/\b\(client\|mc\|minecraft\)\.setScreen(/\1.gui.setScreen(/g
s/\.gui\.setOverlayMessage(/.gui.hud.setOverlayMessage(/g
s/\.gui\.getBossOverlay()/.gui.hud.getBossOverlay()/g

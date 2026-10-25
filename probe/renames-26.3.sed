# Extra mechanical renames for 26.3 on top of renames-26.1.sed + renames-26.2.sed
# (blaze3d -> renderpearl, GLFW -> SDL so no org.lwjgl.glfw, KEYSYM -> KEYBOARD, PoseStack.mulPose(quat) -> rotate).
s/com\.mojang\.blaze3d\.textures\./com.mojang.renderpearl.api.textures./g
s/com\.mojang\.blaze3d\.buffers\./com.mojang.renderpearl.api.buffers./g
s/com\.mojang\.blaze3d\.pipeline\.RenderPipeline/com.mojang.renderpearl.api.pipeline.RenderPipeline/g
s/InputConstants\.Type\.KEYSYM/InputConstants.Type.KEYBOARD/g
s/pose\.mulPose(orientation)/pose.rotate(orientation)/
s/confirmLinkNow(this, \(GITHUB\|LRCLIB\))/confirmLinkNow(this, java.net.URI.create(\1))/
/^import org\.lwjgl\.glfw\.GLFW;/d
s/GLFW\.GLFW_KEY_\([A-Z0-9_]*\)/com.mojang.blaze3d.platform.InputConstants.KEY_\1/g

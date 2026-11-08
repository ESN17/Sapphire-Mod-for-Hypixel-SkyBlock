package com.sorakoi.sbtranslate.mixin.compat;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.sorakoi.sbtranslate.compat.OdinCompat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.*;
@Pseudo @Mixin(targets="com.odtheking.odin.utils.ui.compose.UiHost",remap=false)
public abstract class OdinUiHostMixin {
    @WrapMethod(method="render")
    private void sapphire$render(GuiGraphicsExtractor g,int w,int h,int x,int y,Operation<Void> original){
        OdinCompat.enter();try{original.call(g,w,h,x,y);}finally{OdinCompat.exit();}
    }
}

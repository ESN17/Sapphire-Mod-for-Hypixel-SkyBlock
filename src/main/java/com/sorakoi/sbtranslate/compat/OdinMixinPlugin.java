package com.sorakoi.sbtranslate.compat;
import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import org.spongepowered.asm.service.MixinService;
import java.util.*;
public final class OdinMixinPlugin implements IMixinConfigPlugin {
    public void onLoad(String p){} public String getRefMapperConfig(){return null;}
    public boolean shouldApplyMixin(String target,String mixin){
        if(!FabricLoader.getInstance().isModLoaded("odin"))return false;
        if(!target.startsWith("com.odtheking.odin."))return true;
        try{
            var node=MixinService.getService().getBytecodeProvider().getClassNode(target);
            if(target.endsWith(".UiHost"))return node.methods.stream().anyMatch(m->m.name.equals("render") && m.desc.equals("(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V"));
            return true;
        }catch(Exception|LinkageError e){return false;}
    }
    public void acceptTargets(Set<String>a,Set<String>b){} public List<String> getMixins(){return null;}
    public void preApply(String t,ClassNode n,String m,IMixinInfo i){} public void postApply(String t,ClassNode n,String m,IMixinInfo i){}
}

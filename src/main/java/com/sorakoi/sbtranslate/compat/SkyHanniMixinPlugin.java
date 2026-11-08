package com.sorakoi.sbtranslate.compat;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.spongepowered.asm.mixin.extensibility.*;
import org.spongepowered.asm.service.MixinService;
import java.util.*;

/** Inspect bytecode without loading optional classes. API drift disables the bridge. */
public final class SkyHanniMixinPlugin implements IMixinConfigPlugin {
    public static final String BASE="at.hannibal2.skyhanni.deps.moulconfig.platform.";
    public static final String UNWRAP="(Lat/hannibal2/skyhanni/deps/moulconfig/common/text/StructuredText;)Lnet/minecraft/network/chat/Component;";
    @Override public void onLoad(String pkg){}
    @Override public String getRefMapperConfig(){return null;}
    @Override public boolean shouldApplyMixin(String target,String mixin) {
        if(!FabricLoader.getInstance().isModLoaded("skyhanni"))return false;
        try {
            var provider=MixinService.getService().getBytecodeProvider();
            ClassNode node=provider.getClassNode(target);
            if(target.endsWith("GuiRenderUtils"))return drawCompatible(node);
            if(target.endsWith("TabLine") || target.endsWith("TabListRenderer"))return tabMeasureCompatible(node);
            if(target.endsWith("TextCompatKt"))return has(node,"addChatMessageToChat","(Lnet/minecraft/network/chat/Component;Z)V") &&
                has(node,"addDeletableMessageToChat","(Lnet/minecraft/network/chat/Component;IZ)V");
            if(target.endsWith("Renderable")) {
                String desc=target.endsWith("StringRenderable")?"Ljava/lang/String;":"Lnet/minecraft/network/chat/Component;";
                return has(node,"getText","()"+desc) && has(node,"getScale","()D") && has(node,"getWidth","()I") && has(node,"render","(II)V") &&
                    node.fields.stream().anyMatch(f->f.name.equals("text") && f.desc.equals(desc));
            }
            return compatible(provider.getClassNode(BASE+"MoulConfigFontRenderer"),provider.getClassNode(BASE+"MoulConfigRenderContext"));
        }catch(Exception | LinkageError unavailable){return false;}
    }
    private static boolean has(ClassNode node,String name,String desc){return node.methods.stream().anyMatch(m->m.name.equals(name) && m.desc.equals(desc));}
    public static boolean drawCompatible(ClassNode node) {
        for(String type:List.of("Ljava/lang/String;","Lnet/minecraft/network/chat/Component;"))
            for(String coords:List.of("FF","II"))if(!has(node,"drawString","("+type+coords+"IZ)V"))return false;
        return true;
    }
    public static boolean tabMeasureCompatible(ClassNode node) {
        return node.methods.stream().filter(m->m.name.equals("getWidth") || m.name.equals("drawTabList")).anyMatch(m->{
            for(var i:m.instructions)if(i instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/gui/Font") &&
                call.name.equals("width") && call.desc.equals("(Lnet/minecraft/network/chat/FormattedText;)I"))return true;
            return false;
        });
    }
    public static boolean compatible(ClassNode font,ClassNode render) {
        return invokes(font,"getStringWidth",BASE+"MoulConfigText") &&
               invokes(font,"splitText",BASE+"MoulConfigText") &&
               invokes(render,"drawString",BASE+"MoulConfigPlatform");
    }
    private static boolean invokes(ClassNode node,String name,String owner) {
        return node.methods.stream().filter(m->m.name.equals(name)).anyMatch(m->{
            for(var i:m.instructions)if(i instanceof MethodInsnNode call && call.owner.equals(owner.replace('.','/')) &&
                call.name.equals("unwrap") && call.desc.equals(UNWRAP))return true;
            return false;
        });
    }
    @Override public void acceptTargets(Set<String> mine,Set<String> others){}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
    @Override public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){}
}

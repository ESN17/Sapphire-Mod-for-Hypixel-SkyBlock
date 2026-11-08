package com.sorakoi.sbtranslate.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import java.util.function.BooleanSupplier;

/** Shared styling; buttons retain vanilla focus, narration and input handling. */
final class SettingsTheme {
    static final int PANEL=0xf5101929, SIDEBAR=0xff111e32, CARD=0xff1b2a40,
        HOVER=0xff243954, LINE=0xff30445e, ACCENT=0xff75b8ff, TEXT=0xffeef4ff,
        MUTED=0xffa2b3ca, GREEN=0xff8edac5;
    private SettingsTheme() {}
    static void box(GuiGraphicsExtractor g,int x,int y,int w,int h,int color) {
        if(w<4 || h<4){g.fill(x,y,x+w,y+h,color);return;}
        g.fill(x+2,y,x+w-2,y+h,color);g.fill(x,y+2,x+w,y+h-2,color);
    }
    static void frame(GuiGraphicsExtractor g,int x,int y,int w,int h,String title,String subtitle) {
        g.fill(0,0,g.guiWidth(),g.guiHeight(),0xa008101d);
        box(g,x+3,y+4,w,h,0x60000000);box(g,x,y,w,h,PANEL);
        g.fill(x+1,y+1,x+w-1,y+2,0xff456487);
        var font=Minecraft.getInstance().font;
        g.text(font,title,x+16,y+14,TEXT,false);
        if(!subtitle.isEmpty())g.text(font,font.plainSubstrByWidth(subtitle,w-32),x+16,y+30,MUTED,false);
    }
    static void searchBox(net.minecraft.client.gui.components.EditBox box) {box.setTextColor(TEXT);box.setTextShadow(false);}
    static Button button(String text,int x,int y,int w,int h,Runnable action) {return new FlatButton(text,x,y,w,h,action,()->false,false);}
    static Button primary(String text,int x,int y,int w,int h,Runnable action) {return new FlatButton(text,x,y,w,h,action,()->false,true);}
    static Button navigation(String text,int x,int y,int w,int h,Runnable action,BooleanSupplier selected) {return new FlatButton(text,x,y,w,h,action,selected,false);}
    private static final class FlatButton extends Button {
        private final BooleanSupplier selected;private final boolean primary;
        FlatButton(String text,int x,int y,int w,int h,Runnable action,BooleanSupplier selected,boolean primary) {
            super(x,y,w,h,Component.literal(text),b->action.run(),DEFAULT_NARRATION);this.selected=selected;this.primary=primary;
        }
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta) {
            boolean chosen=selected.getAsBoolean();int x=getX(),y=getY();
            box(g,x,y,width,height,!active?0xff172234:primary?0xff315f91:chosen?0xff263f60:isHoveredOrFocused()?HOVER:CARD);
            if(chosen)g.fill(x,y+5,x+2,y+height-5,ACCENT);
            if(isFocused())g.outline(x,y,width,height,ACCENT);
            var font=Minecraft.getInstance().font;String label=font.plainSubstrByWidth(getMessage().getString(),width-10);
            g.text(font,label,x+(width-font.width(label))/2,y+(height-8)/2,active?TEXT:MUTED,false);
        }
    }
    static final class SettingCard extends Button {
        private final String heading,description;private final BooleanSupplier value;
        SettingCard(String title,String description,int x,int y,int w,int h,BooleanSupplier value,Runnable action) {
            super(x,y,w,h,Component.literal(title),b->action.run(),DEFAULT_NARRATION);
            this.heading=title;this.description=description;this.value=value;
            setTooltip(Tooltip.create(Component.literal(description)));setTooltipDelay(java.time.Duration.ofMillis(600));
        }
        @Override protected net.minecraft.network.chat.MutableComponent createNarrationMessage() {
            return Component.literal(heading+"，"+(value==null?"打开":value.getAsBoolean()?"开启":"关闭")+"。"+description);
        }
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta) {
            int x=getX(),y=getY();var font=Minecraft.getInstance().font;
            box(g,x,y,width,height,isHoveredOrFocused()?HOVER:CARD);
            if(isFocused())g.outline(x,y,width,height,ACCENT);
            int titleY=y+(height<36?6:9);
            g.text(font,font.plainSubstrByWidth(heading,Math.max(20,width-(value==null?46:70))),x+10,titleY,TEXT,false);
            String detail=description;int max=width-20;
            if(font.width(detail)>max)detail=font.plainSubstrByWidth(detail,Math.max(8,max-font.width("…")))+"…";
            if(height>=32)g.text(font,detail,x+10,y+(height<36?21:25),MUTED,false);
            if(value==null)g.text(font,"›",x+width-18,titleY,ACCENT,false);
            else {
                boolean on=value.getAsBoolean();int sx=x+width-36,sy=y+7;
                g.text(font,on?"开":"关",sx-15,sy+2,on?GREEN:MUTED,false);
                box(g,sx,sy,26,13,on?0xff477fae:0xff44546a);box(g,sx+(on?15:2),sy+2,9,9,on?0xffe7f4ff:0xffa6b1c3);
            }
        }
    }
}

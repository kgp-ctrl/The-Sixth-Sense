package com.surins;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/** Optional presentation controls; presets never reset custom positions or turn warnings off. */
final class PresentationScreen extends Screen {
    private final Screen parent;
    private String status = "";
    PresentationScreen(Screen parent) { super(SixthSenseLanguage.text("config.presentation")); this.parent = parent; }
    @Override protected void init() {
        int w = Math.min(300, width-24), x = (width-w)/2, top = Math.max(28,(height-190)/2);
        String[] presets = {"simple", "balanced", "detailed"};
        for (int i=0;i<3;i++) {
            final int index=i;
            addDrawableChild(ButtonWidget.builder(SixthSenseLanguage.text("preset."+presets[i]), b -> {
                SixthSenseConfig.applyPreset(index); status="profile.applied"; clearAndInit();
            }).dimensions(x+i*(w/3),top,w/3-2,20)
                    .tooltip(Tooltip.of(SixthSenseLanguage.text("preset.help"))).build());
        }
        toggle(x,top+29,w,"config.reduced_motion",SixthSenseConfig.instance.reduceMotion, () -> SixthSenseConfig.instance.reduceMotion=!SixthSenseConfig.instance.reduceMotion);
        toggle(x,top+53,w,"config.priority",SixthSenseConfig.instance.prioritizeThreats, () -> SixthSenseConfig.instance.prioritizeThreats=!SixthSenseConfig.instance.prioritizeThreats);
        addDrawableChild(ButtonWidget.builder(SixthSenseLanguage.text("config.opacity",Math.round(SixthSenseConfig.instance.warningOpacity*100)),b->{
            int step=Math.round(SixthSenseConfig.instance.warningOpacity*5)-1;
            SixthSenseConfig.instance.warningOpacity=step<1?1:step/5f;SixthSenseConfig.save();clearAndInit();
        }).dimensions(x,top+77,w,20).build());
        addDrawableChild(ButtonWidget.builder(SixthSenseLanguage.text("profile.save"),b->{
            status=SixthSenseConfig.saveProfile()?"profile.saved":"profile.failed";clearAndInit();
        }).dimensions(x,top+109,w/2-2,20).build());
        addDrawableChild(ButtonWidget.builder(SixthSenseLanguage.text("profile.restore"),b->{
            status=SixthSenseConfig.restoreProfile()?"profile.restored":"profile.failed";clearAndInit();
        }).dimensions(x+w/2+2,top+109,w/2-2,20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"),b->close()).dimensions(width/2-45,height-26,90,20).build());
    }
    private void toggle(int x,int y,int w,String key,boolean current,Runnable action) {
        Text label=SixthSenseLanguage.text(key).append(": ").append(SixthSenseLanguage.text(current?"state.on":"state.off"));
        addDrawableChild(ButtonWidget.builder(label,b->{action.run();SixthSenseConfig.save();clearAndInit();})
                .dimensions(x,y,w,20).tooltip(Tooltip.of(SixthSenseLanguage.text(key+"_help"))).build());
    }
    @Override public void renderBackground(DrawContext c,int x,int y,float delta) { }
    @Override public void render(DrawContext c,int x,int y,float delta) {
        super.renderBackground(c,x,y,delta);
        c.drawCenteredTextWithShadow(textRenderer,title,width/2,9,VanillaUi.TEXT);
        if(!status.isEmpty())c.drawCenteredTextWithShadow(textRenderer,SixthSenseLanguage.text(status),width/2,height-44,0xFFD0D8DC);
        super.render(c,x,y,delta);
    }
    @Override public boolean shouldPause(){return false;}
    @Override public void close(){client.setScreen(new SixthSenseTechnicalScreen(parent instanceof SixthSenseTechnicalScreen ? ((SixthSenseTechnicalScreen)parent).parentScreen() : parent));}
}

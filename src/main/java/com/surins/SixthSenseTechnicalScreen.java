package com.surins;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pinned general controls and grouped warning rows, shared by H and Mod Menu. */
final class SixthSenseTechnicalScreen extends Screen {
    private final Screen parent;
    private final SettingsCatalog.Category category;
    private SettingsCatalog.Category general, special;
    private final List<WarningRow> visibleWarnings=new ArrayList<>();
    private final List<Heading> headings=new ArrayList<>();
    private int page,pageSize,rowHeight,left,rowWidth,entriesTop,generalRowHeight,pages,contentBottom,specialY,specialRowHeight;
    private boolean refreshed;
    private record WarningRow(SettingsCatalog.Category category,int x,int y,int width,int height) { }
    private record Heading(String key,int y) { }
    private record CatalogRow(String heading,List<SettingsCatalog.Category> entries,int height) { }

    SixthSenseTechnicalScreen(Screen parent) { this(parent,null); }
    private SixthSenseTechnicalScreen(Screen parent,SettingsCatalog.Category category) {
        super(SixthSenseLanguage.text("technical.title")); this.parent=parent;this.category=category;
    }
    Screen parentScreen() { return parent; }
    @Override protected void init() {
        net.minecraft.client.option.KeyBinding.unpressAll();
        if(client.player!=null){client.player.input.movementForward=0;client.player.input.movementSideways=0;client.player.input.jumping=false;client.player.input.sneaking=false;}
        if(!refreshed) { SettingsCatalog.refreshPositions(client);refreshed=true; }
        visibleWarnings.clear();headings.clear();
        var all=SettingsCatalog.categories();general=all.getFirst();
        rowWidth=Math.min(396,Math.max(140,width-24));left=(width-rowWidth)/2;
        generalRowHeight=20;
        for(var entry:general.entries())generalRowHeight=Math.max(generalRowHeight,labelHeight(entry));
        for(int i=0;i<general.entries().size();i++)addControl(general.entries().get(i),42+i*generalRowHeight);
        addDrawableChild(ButtonWidget.builder(Text.literal("R"),b->reset(general))
                .dimensions(left+rowWidth-18,24,18,16)
                .tooltip(Tooltip.of(SixthSenseLanguage.text("config.reset_category"))).build());
        addDrawableChild(ButtonWidget.builder(SixthSenseLanguage.text("config.presentation"),
                b->client.setScreen(new PresentationScreen(this)))
                .dimensions(left+rowWidth-132,24,110,16).build());
        special=SettingsCatalog.special();
        specialY=46+general.entries().size()*generalRowHeight;
        specialRowHeight=20;
        for(var entry:special.entries())specialRowHeight=Math.max(specialRowHeight,labelHeight(entry));
        for(int i=0;i<special.entries().size();i++)addControl(special.entries().get(i),specialY+16+i*specialRowHeight);
        entriesTop=specialY+16+special.entries().size()*specialRowHeight+24;
        contentBottom=height-32;
        if(category==null) {
            var categories=all.subList(1,all.size());
            int columns=rowWidth>=360?2:1, cellWidth=(rowWidth-(columns-1)*8)/columns;
            var sheets=paginate(categories,columns,cellWidth,contentBottom-entriesTop);
            if(sheets.size()>1) { contentBottom-=20;sheets=paginate(categories,columns,cellWidth,contentBottom-entriesTop); }
            pages=sheets.size();page=Math.min(page,pages-1);
            int y=entriesTop;
            for(var row:sheets.get(page)) {
                if(row.heading()!=null) headings.add(new Heading(row.heading(),y));
                else for(int col=0;col<row.entries().size();col++) {
                    var target=row.entries().get(col);int x=left+col*(cellWidth+8);
                    visibleWarnings.add(new WarningRow(target,x,y,cellWidth,row.height()));
                    addDrawableChild(new ToggleSwitch(x+cellWidth-66,y+(row.height()-18)/2,
                            SixthSenseLanguage.text(target.key()),target.enabled()));
                    addDrawableChild(new SettingsButton(x+cellWidth-20,y+(row.height()-18)/2,target));
                }
                y+=row.height();
            }
        } else {
            rowHeight=20;
            for(var entry:category.options())rowHeight=Math.max(rowHeight,labelHeight(entry));
            int count=category.options().size();
            pageSize=Math.max(1,(contentBottom-entriesTop)/(rowHeight+2));
            if(count>pageSize) {contentBottom-=20;pageSize=Math.max(1,(contentBottom-entriesTop)/(rowHeight+2));}
            pages=Math.max(1,(count+pageSize-1)/pageSize);page=Math.min(page,pages-1);
            for(int i=page*pageSize;i<Math.min(count,(page+1)*pageSize);i++)addControl(category.options().get(i),entriesTop+(i-page*pageSize)*(rowHeight+2));
        }
        if(pages>1) {
            var previous=addDrawableChild(ButtonWidget.builder(Text.literal("<"),b->turnPage(-1))
                    .dimensions(left,height-49,20,16).build());previous.active=page>0;
            var next=addDrawableChild(ButtonWidget.builder(Text.literal(">"),b->turnPage(1))
                    .dimensions(left+rowWidth-20,height-49,20,16).build());next.active=page<pages-1;
        }
        if(category!=null)addDrawableChild(ButtonWidget.builder(SixthSenseLanguage.text("config.reset_category"),b->reset(category))
                .dimensions(width/2+4,height-25,Math.min(138,rowWidth/2-4),18).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.back"),b->close())
                .dimensions(category==null?width/2-45:left,height-25,category==null?90:Math.min(138,rowWidth/2-4),18).build());
    }
    private int labelHeight(SettingsCatalog.Entry entry) {
        return SixthSenseLanguage.wrap(textRenderer, SixthSenseLanguage.text(entry.key()),rowWidth-76).size()*10+6;
    }
    private List<List<CatalogRow>> paginate(List<SettingsCatalog.Category> categories,int columns,int cellWidth,int available) {
        List<List<CatalogRow>> sheets=new ArrayList<>();List<CatalogRow> sheet=new ArrayList<>();sheets.add(sheet);
        int used=0;available=Math.max(40,available);
        for(var group:SettingsCatalog.Group.values()) {
            var members=categories.stream().filter(c->c.group()==group).toList();
            boolean needsHeading=true;
            for(int i=0;i<members.size();i+=columns) {
                var entries=members.subList(i,Math.min(members.size(),i+columns));int h=20;
                for(var c:entries)h=Math.max(h,SixthSenseLanguage.wrap(textRenderer, SixthSenseLanguage.text(c.key()),cellWidth-94).size()*10+6);
                if(used+h+(needsHeading?20:0)>available && !sheet.isEmpty()) {
                    sheet=new ArrayList<>();sheets.add(sheet);used=0;needsHeading=true;
                }
                if(needsHeading) {sheet.add(new CatalogRow(group.key,List.of(),20));used+=20;needsHeading=false;}
                sheet.add(new CatalogRow(null,entries,h));used+=h;
            }
        }
        return sheets;
    }
    private void reset(SettingsCatalog.Category target) { target.entries().forEach(SettingsCatalog.Entry::reset);SixthSenseConfig.save();clearAndInit(); }
    private void addControl(SettingsCatalog.Entry entry,int y) {
        Tooltip tooltip=entry.help().isEmpty()?null:Tooltip.of(SixthSenseLanguage.text(entry.help()));
        if(entry instanceof SettingsCatalog.Toggle toggle) {
            addDrawableChild(new ToggleSwitch(left+rowWidth-44,y,SixthSenseLanguage.text(entry.key()),toggle));
        } else if(entry instanceof SettingsCatalog.NumberValue number) {
            var field=new TextFieldWidget(textRenderer,left+rowWidth-62,y,62,18,SixthSenseLanguage.text(entry.key()));
            field.setMaxLength(12);field.setText(number.integer()?Integer.toString((int)number.read().getAsDouble()):String.format(Locale.ROOT,"%.2f",number.read().getAsDouble()));
            field.setTooltip(tooltip!=null?tooltip:Tooltip.of(SixthSenseLanguage.text("config.number_help",number.min(),number.max())));
            field.setChangedListener(text->{try {
                double value=Double.parseDouble(text.replace(',','.'));
                boolean valid=Double.isFinite(value)&&value>=number.min()&&value<=number.max()&&(!number.integer()||value==Math.rint(value));
                field.setEditableColor(valid?0xE0E0E0:0xFF7777);
                if(valid){number.write().accept(value);SixthSenseConfig.save();}
            }catch(NumberFormatException ignored){field.setEditableColor(0xFF7777);}});addDrawableChild(field);
        }
    }
    private void turnPage(int direction) {page=Math.max(0,Math.min(pages-1,page+direction));clearAndInit();}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        if(vertical!=0&&y>=entriesTop){turnPage(vertical>0?-1:1);return true;}return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean shouldPause(){return true;}
    @Override public void renderBackground(DrawContext context,int mouseX,int mouseY,float delta) { }
    @Override public void render(DrawContext context,int mouseX,int mouseY,float delta) {
        super.renderBackground(context,mouseX,mouseY,delta);
        context.drawCenteredTextWithShadow(textRenderer,title,width/2,8,VanillaUi.TEXT);
        heading(context,"config.general",left,27,Math.max(40,rowWidth-140));
        for(int i=0;i<general.entries().size();i++)label(context,general.entries().get(i),42+i*generalRowHeight);
        heading(context,"config.special",left,specialY,rowWidth);
        for(int i=0;i<special.entries().size();i++)label(context,special.entries().get(i),specialY+16+i*specialRowHeight);
        heading(context,category==null?"config.warnings":category.key(),left,entriesTop-16,rowWidth);
        for(var heading:headings)heading(context,heading.key(),left,heading.y()+5,rowWidth);
        for(var row:visibleWarnings) {
            context.fill(row.x(),row.y(),row.x()+row.width()-70,row.y()+row.height()-2,VanillaUi.PANEL);
            context.getMatrices().push();context.getMatrices().translate(row.x()+4,row.y()+(row.height()-12)/2f-1,0);
            context.getMatrices().scale(.75f,.75f,1);context.drawItem(row.category().icon(client),0,0);context.getMatrices().pop();
            var lines=SixthSenseLanguage.wrap(textRenderer, SixthSenseLanguage.text(row.category().key()),row.width()-94);
            int y=row.y()+(row.height()-lines.size()*10)/2;
            for(var line:lines){context.drawTextWithShadow(textRenderer,line,row.x()+20,y,0xFFE0E5E8);y+=10;}
        }
        if(category!=null) {
            int count=category.options().size();
            for(int i=page*pageSize;i<Math.min(count,(page+1)*pageSize);i++)label(context,category.options().get(i),entriesTop+(i-page*pageSize)*(rowHeight+2));
            if(count==0) {
                int y=entriesTop+4;
                for(var line:SixthSenseLanguage.wrap(textRenderer, SixthSenseLanguage.text(category.enabled().help()),rowWidth)){context.drawTextWithShadow(textRenderer,line,left,y,0xFFB6C2CB);y+=11;}
            }
            if(category.key().equals("config.phantom")) {
                int bottom=entriesTop+Math.min(pageSize,count-page*pageSize)*(rowHeight+2),available=contentBottom-bottom;
                if(available>24){RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();PhantomThreatTracker.drawIcon(context,width/2f,bottom+available/2f,Math.min(PhantomThreatTracker.markerWidth(client),available-4),1);RenderSystem.disableBlend();}
            }
        }
        if(pages>1)context.drawCenteredTextWithShadow(textRenderer,Text.literal((page+1)+" / "+pages),width/2,height-45,0xFFAEBCC4);
        super.render(context,mouseX,mouseY,delta);
    }
    private void label(DrawContext context,SettingsCatalog.Entry entry,int y) {
        for(var line:SixthSenseLanguage.wrap(textRenderer, SixthSenseLanguage.text(entry.key()),rowWidth-76)){context.drawTextWithShadow(textRenderer,line,left,y+4,0xFFD0D8DC);y+=10;}
    }
    @Override public void close(){SixthSenseConfig.save();client.setScreen(parent);}

    private final class SettingsButton extends ButtonWidget {
        SettingsButton(int x,int y,SettingsCatalog.Category target) {
            super(x,y,20,18,SixthSenseLanguage.text("config.open",SixthSenseLanguage.text(target.key())),
                    b->client.setScreen(new SixthSenseTechnicalScreen(SixthSenseTechnicalScreen.this,target)),DEFAULT_NARRATION_SUPPLIER);
            setTooltip(Tooltip.of(getMessage()));
        }
        @Override public void drawMessage(DrawContext context,net.minecraft.client.font.TextRenderer renderer,int color) {
            for(int i=0;i<3;i++)context.fill(getX()+5,getY()+5+i*3,getX()+13,getY()+6+i*3,1,color);
        }
    }

    private void heading(DrawContext context,String key,int x,int y,int length){
        var text=SixthSenseLanguage.text(key).formatted(net.minecraft.util.Formatting.BOLD);
        context.drawTextWithShadow(textRenderer,text,x,y,VanillaUi.TEXT);
        int start=x+textRenderer.getWidth(text)+8;
        if(start<x+length)context.fill(start,y+4,x+length,y+5,VanillaUi.BORDER);
    }
    /** Native Minecraft button; text plus tooltip retain keyboard and screen-reader access. */
    private static final class ToggleSwitch extends ButtonWidget {
        private final SettingsCatalog.Toggle entry;
        private final Text label;
        private Boolean lastState;
        ToggleSwitch(int x,int y,Text label,SettingsCatalog.Toggle entry) {
            super(x,y,44,18,Text.empty(),b->{entry.write().accept(!entry.read().getAsBoolean());SixthSenseConfig.save();((ToggleSwitch)b).refresh();},DEFAULT_NARRATION_SUPPLIER);
            this.label=label;this.entry=entry;refresh();
        }
        private void refresh(){
            boolean enabled=entry.read().getAsBoolean();
            if(lastState!=null && lastState==enabled)return;
            lastState=enabled;
            var state=SixthSenseLanguage.text(enabled?"state.on":"state.off");
            setMessage(state);setTooltip(Tooltip.of(label.copy().append(": ").append(state)
                    .append(entry.help().isEmpty()?Text.empty():Text.literal("\n").append(SixthSenseLanguage.text(entry.help())))));
        }
    }
}

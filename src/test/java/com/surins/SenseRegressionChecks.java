package com.surins;

import net.minecraft.nbt.NbtCompound;
import java.util.UUID;

final class SenseRegressionChecks {
    static void run() {
        sunExposureTransitions();
        check(Math.abs(SenseWhisper.carriedEntrance(9.775f,10f)-.225f)<.0001f,
                "A replacement during exit jumped back to full opacity");
        check(SenseWhisper.carriedEntrance(.1f,10f)==.1f, "Replacement restarted entrance");
        check(SenseWhisper.carriedEntrance(11,10)==0, "Expired thought carried animation state");
        for (int fps : new int[]{5, 10, 30, 60, 144}) {
            float elapsed = 0;
            long previous = 1_000_000_000L;
            for (int frame=1; frame<=fps; frame++) {
                long now = 1_000_000_000L + Math.round(frame * 1e9 / fps);
                elapsed += SenseWhisper.frameSeconds(previous, now); previous = now;
            }
            check(Math.abs(elapsed - 1) < .0001f, "Caption timing slowed at " + fps + " FPS");
        }
        check(SenseWhisper.frameSeconds(0, 9_000_000_000L) == 0, "Menu resume consumed hidden time");
        whisperRelicColors();
        check(SenseRules.XP_COST==(5*18-38)+(5*19-38), "XP cost is not two levels at 20");
        check(Math.abs(Math.acos(SenseRules.SUN_DOT)*2-Math.acos(.94))<1e-9,"Sun tolerance was not halved");
        check("level".equals(SenseRules.rejection(true, 19, 0, 0, false)), "Level gate failed");
        check(SenseRules.rejection(true, 20, 0, 0, false) == null, "Level 20 rejected");
        check("targeted".equals(SenseRules.rejection(true, 20, 0, 0, true)), "Targeted player accepted");
        check("tired".equals(SenseRules.rejection(true, 20, 0, 1, false)), "Fatigue bypass");
        check("active".equals(SenseRules.rejection(true, 20, 10, 1, true)), "Active ability restarted");
        SenseState state = new SenseState(); UUID id = UUID.randomUUID();
        var entry = state.player(id); entry.start();
        check(entry.active == 1200 && entry.nights == 1 && SenseRules.RADIUS == 52, "Duration or one-night rest changed");
        check(!entry.rest(99), "Interrupted sleep counted");
        SenseState restored = SenseState.read(state.writeNbt(new NbtCompound(), null), null);
        entry = restored.player(id);
        check(entry.nights == 1 && entry.active == 0, "Reconnect lost fatigue or resumed sensing");
        check(entry.rest(100) && entry.nights == 0, "One completed sleep did not fully recover");
        check(!entry.rest(100), "Recovery applied twice");
        check(SenseRules.rejection(true, 20, entry.active, entry.nights, false) == null, "Recovered eyes rejected");
        entry.start();
        check(entry.rest(100) && entry.active == 0 && entry.nights == 0,
                "A second completed sleep after time reset did not recover or cancel active sensing");
        NbtCompound legacy = new NbtCompound(), data = new NbtCompound();
        data.putInt("nights", 3); legacy.put(id.toString(), data);
        check(SenseState.read(legacy, null).player(id).nights == 1, "Old three-night save was not migrated");
        data.putInt("active", 600);
        var migrated = SenseState.read(legacy, null).player(id);
        check(migrated.active == 0 && migrated.nights == 1, "Interrupted active save resumed on reload");
        check(SenseGameplay.behind(new net.minecraft.util.math.Vec3d(0,0,1), new net.minecraft.util.math.Vec3d(0,0,-2)), "Rear approach rejected");
        check(!SenseGameplay.behind(new net.minecraft.util.math.Vec3d(0,0,1), new net.minecraft.util.math.Vec3d(0,0,2)), "Front approach counted as rear");
        check(!SenseGameplay.behind(new net.minecraft.util.math.Vec3d(0,0,1), new net.minecraft.util.math.Vec3d(2,0,0)), "Side approach counted as rear");
        for (int fps : new int[]{20, 60, 144}) {
            float opacity = 0, gathering = 0;
            for (int i = 0; i < fps; i++) {
                opacity = SenseVisualMath.approach(opacity, 1, 1f / fps / .4f);
                gathering = SenseVisualMath.approach(gathering, 1, 1f / fps / .45f);
            }
            check(opacity == 1 && gathering == 1, "Animation depends on FPS");
            for (int i = 0; i < fps; i++) opacity = SenseVisualMath.approach(opacity, 0, 1f / fps / .4f);
            check(opacity == 0, "Fade failed to clear");
        }
        check(SenseVisualMath.approach(.5f, 1, 0) == .5f, "Paused animation advanced");
        System.out.println("Sensing checks passed: XP cost, sun alignment, level/duration, one-night recovery, legacy save migration, persistent state and FPS-independent transitions.");
    }
    private static void sunExposureTransitions() {
        SunExposure sun = new SunExposure();
        for (int tick=1;tick<=600;tick++) {
            int milestone=sun.tick(true);
            check(milestone==(tick==140?7:tick==300?15:tick==600?30:0), "Sun dialogue timing changed");
            if(tick<=40)check(sun.noise()==0,"Immediate CRT returned");
            check(sun.noise()>=0 && sun.noise()<=1,"Invalid sunlight intensity");
        }
        check(sun.closing && !sun.ready(),"Head moved before eye closure");
        for(int tick=0;tick<12;tick++)sun.tick(true);
        check(sun.closure()==1 && !sun.ready(),"Head moved before black frame was displayed");
        sun.blackFrame=true;
        check(sun.ready(),"Black frame did not unlock gaze recovery");
        sun.lookedAway();
        for(int tick=0;tick<12;tick++)sun.tick(false);
        check(sun.closure()==0 && sun.noise()==0 && !sun.ready(),"Eyes failed to reopen");
        for(int tick=0;tick<300;tick++)sun.tick(true);
        sun.tick(false);
        check(sun.ticks==0 && sun.noise()==0,"Looking away did not reset exposure");
        for(int tick=0;tick<605;tick++)sun.tick(true);
        sun.tick(false);
        check(!sun.ready() && sun.opening,"Cancelled sun gaze could still rotate the player");
        sun.clear();
        check(sun.closure()==0 && sun.noise()==0 && !sun.opening,"World/recovery reset leaked eye effects");
        check(!SixthSenseConfig.decode("{\"settingsTheme\":\"ice\"}").innerVoice, "Missing inner voice preference must default off");
        check(!SixthSenseConfig.decode("{\"innerVoice\":false}").innerVoice, "Inner voice setting did not load");
        check(!new SixthSenseConfig().innerVoice, "Fresh config enabled experimental voice");
        check(SixthSenseConfig.decode("{\"innerVoice\":true}").innerVoice, "Explicit inner voice choice was overwritten");
        for (String key : new String[]{"started","tired","level","targeted","unavailable"})
            check(SenseWhisper.basicNotice(key), "Ability notice lost in subtitle mode");
        for (String key : new String[]{"hungry","footing","sun","sun_7","sun_15","sun_30"})
            check(!SenseWhisper.basicNotice(key), "Story thought leaked to subtitle mode");
    }
    private static void whisperRelicColors() {
        int overlayPixels = 0;
        for (int y=0;y<9;y++) for (int x=0;x<9;x++) {
            int color=WhisperRelicMath.heartPixel(x,y);
            if(color!=0)overlayPixels++;
            if(x==0 || x==8 || y==0 || y==8)check(color==0,"Heart material changed container outline");
        }
        check(overlayPixels==34,"Heart material must match vanilla filled pixels");
        check(SenseWhisper.orbProgress(0)==0 && SenseWhisper.panelProgress(.42f)==0,"Panel opened before sphere formed");
        check(SenseWhisper.orbProgress(.42f)==1 && SenseWhisper.panelProgress(.66f)==1,"Extraction did not complete");
        for(int step=0;step<=66;step++) {
            float t=step/100f;
            check(Math.abs(SenseWhisper.orbProgress(SenseWhisper.carriedEntrance(t,10))-
                    SenseWhisper.orbProgress(SenseWhisper.carriedEntrance(10-t,10)))<.0001f,"Exit does not reverse extraction");
        }
        for (int iris : new int[]{0xA34BE0, 0x3974BB, 0x398A45, 0x714225}) {
            int[] skin = new int[64*64];
            java.util.Arrays.fill(skin, 0xFFD5A882);
            skin[12*64+9] = skin[12*64+14] = 0xFF000000 | iris;
            check(WhisperRelicMath.eyeColor(64, 64, (x,y)->skin[y*64+x]) == iris, "Iris color lost");
            check(WhisperRelicMath.eyeColor(128, 128, (x,y)->skin[(y/2)*64+x/2]) == iris, "Scaled skin color lost");
            check(WhisperRelicMath.eyeColor(64, 32, (x,y)->skin[y*64+x]) == iris, "Legacy skin color lost");
        }
        int[] face = new int[64*64]; java.util.Arrays.fill(face, 0xFFD5A882);
        face[12*64+9] = face[12*64+14] = 0xFFFFFFFF;
        check(WhisperRelicMath.eyeColor(64,64,(x,y)->face[y*64+x])==0xFFFFFF, "White eyes were rejected");
        face[12*64+10] = face[12*64+13] = 0xFFA34BE0;
        check(WhisperRelicMath.eyeColor(64,64,(x,y)->face[y*64+x])==0xA34BE0, "Eye whites replaced the iris");
        face[12*64+9] = face[12*64+14] = 0xFFD5A882;
        face[12*64+13] = 0xFFD5A882; face[11*64+13] = 0xFFA34BE0;
        check(WhisperRelicMath.eyeColor(64,64,(x,y)->face[y*64+x])==WhisperRelicMath.NEUTRAL, "Asymmetric face marks mistaken for eyes");
        check(WhisperRelicMath.eyeColor(64, 64, (x,y)->0xFFD5A882) == WhisperRelicMath.NEUTRAL, "Skin tone mistaken for iris");
        check(WhisperRelicMath.eyeColor(64, 64, (x,y)->0) == WhisperRelicMath.NEUTRAL, "Transparent skin guessed a color");
        check(WhisperRelicMath.eyeColor(32, 32, (x,y)->{throw new AssertionError("Invalid skin was read");}) == WhisperRelicMath.NEUTRAL,
                "Unsupported skin did not fall back");
        check(WhisperRelicMath.pixel(0,0,0xA34BE0) == 0, "Rune has opaque corners");
        check(WhisperRelicMath.swapRedBlue(WhisperRelicMath.swapRedBlue(0x7F123456))==0x7F123456, "Texture color channel conversion failed");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}

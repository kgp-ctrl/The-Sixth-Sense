package com.surins;

import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class CrosshairRegressionChecks {
    static void run() throws Exception {
        raidSelection(); trajectories(); reticleTiming(); phantomEyes(); safeSettings();
        System.out.println("Crosshair checks passed: raid selection, blocked/curved shots, 0.4 s corner playback and arc target/damage policy, Phantom eyes, config recovery.");
    }
    private static void raidSelection() {
        var selection = new RaidSelection<String>();
        selection.select("visible", 25, true, true);
        selection.select("hidden", 9, true, false);
        check(selection.target().equals("hidden") && !selection.visible(), "Previous target visibility leaked");
        selection.select("dead", 1, false, true);
        check(selection.target().equals("hidden"), "Dead raider replaced living candidate");
        var range = new RaidSelection<String>();
        range.select("diagonal", 100*100+100*100, true, true);
        check(range.target() == null, "Box corner created a phantom raid target");
        range.select("edge", 128*128, true, false);
        check(range.target().equals("edge"), "Exact range boundary excluded");
    }
    private record Obstacle(Box box, boolean living) { }
    private static BallisticTrace.Scene scene(boolean water, Obstacle... obstacles) {
        return new BallisticTrace.Scene() {
            public boolean water(Vec3d p) { return water; }
            public BallisticTrace.Hit hit(Vec3d a, Vec3d b) {
                double distance=Double.POSITIVE_INFINITY;
                BallisticTrace.Hit result=BallisticTrace.Hit.NONE;
                for(var obstacle:obstacles) {
                    var point=obstacle.box().raycast(a,b);
                    double d=obstacle.box().contains(a)?0:point.map(a::squaredDistanceTo).orElse(Double.POSITIVE_INFINITY);
                    if(d<distance) { distance=d; result=obstacle.living()?BallisticTrace.Hit.TARGET:BallisticTrace.Hit.BLOCKED; }
                }
                return result;
            }
        };
    }
    private static void trajectories() {
        Vec3d start=new Vec3d(0,1.52,0), shot=new Vec3d(0,0,3);
        var target=new Obstacle(new Box(-.4,0,9.5,.4,1.8,10.5),true);
        check(BallisticTrace.hitsTarget(start,shot,scene(false,target)),"Near living target missed");
        var wall=new Obstacle(new Box(-1,0,5,1,3,5.5),false);
        check(!BallisticTrace.hitsTarget(start,shot,scene(false,target,wall)),"Shot passed through a wall");
        check(!BallisticTrace.hitsTarget(start,new Vec3d(3,0,0),scene(false,target)),"Turning away retained a hit");
        check(!BallisticTrace.hitsTarget(start,new Vec3d(0,0,.1),scene(false,target)),"Uncharged shot has full-bow range");
        var far=new Obstacle(new Box(-.3,-1,49.5,.3,1,50.5),true);
        check(!BallisticTrace.hitsTarget(start,shot,scene(false,far)),"Arrow drop was ignored");
        check(!BallisticTrace.hitsTarget(start,shot,scene(true,target)),"Water drag was ignored");
        check(!BallisticTrace.hitsTarget(start,shot,scene(false)),"Empty scene reports a target");
        check(!BallisticTrace.hitsTarget(start,shot,scene(false,new Obstacle(target.box(),false))),"Nonliving obstacle is a living hit");
    }
    private static void reticleTiming() {
        long now=10_000_000_000L;
        for(int fps:new int[]{20,60,144}) {
            var state=new ReticleAnimation();state.target(true,now);
            near(state.sample(now).progress(),0,"Indicator did not begin hidden");
            for(int frame=0;frame<4;frame++) {
                var sample=state.sample(now+frame*100_000_000L+50_000_000L);
                check(sample.sprite()==frame,"Indicator step skipped or played backward");
            }
            near(state.sample(now+400_000_000L).progress(),1,"Indicator does not complete inward slide in 0.4 seconds");
            state.target(false,now+1_000_000_000L);
            float previous=1;
            for(int i=0;i<=fps;i++) {
                var frame=state.sample(now+1_000_000_000L+i*400_000_000L/fps);
                check(frame.progress()<=previous+.001,"Retraction progress increased");previous=frame.progress();
            }
            near(state.sample(now+1_400_000_000L).alpha(),0,"Retraction did not finish in 0.4 seconds");
        }
        var state=new ReticleAnimation();state.target(true,now);state.target(false,now+100_000_000L);
        var before=state.sample(now+200_000_000L);state.target(true,now+200_000_000L);
        near(state.sample(now+200_000_000L).progress(),before.progress(),"Reacquisition jumps frames");
        state.reset();near(state.sample(now+310_000_000L).alpha(),0,"World reset retained indicator");
        check(!ArcTargetMath.belowReticle(-4,-4,4,12,640,360),"Direct reticle target glows");
        check(!ArcTargetMath.belowReticle(-4,7,4,12,640,360),"Reticle edge target glows");
        check(ArcTargetMath.belowReticle(-4,12,4,24,640,360),"Arced target below reticle not selected");
        check(!ArcTargetMath.belowReticle(-4,200,4,210,640,360),"Offscreen target selected");
        near(BowDamageEstimate.damage(3,0,0,0,0,0),6,"Plain-arrow conservative damage wrong");
        near(BowDamageEstimate.damage(3,2,0,0,0,0),9,"1.21.1 Power modifier wrong");
        check(BowDamageEstimate.damage(3,0,20,8,0,0)<6,"Armor not considered");
        check(BowDamageEstimate.damage(3,0,0,0,20,0)<6,"Protection not considered");
        near(BowDamageEstimate.damage(3,0,0,0,0,5),0,"Resistance immunity ignored");
        check(!ArcTargetMath.outsideNearRange(new Box(-.5,0,1,.5,2,2),new Vec3d(0,1.6,0)),"Point-blank indicator allowed");
        check(!ArcTargetMath.outsideNearRange(new Box(-1,0,15,1,3,30),new Vec3d(0,1.6,0)),"Large nearby target passed center-distance check");
        check(ArcTargetMath.outsideNearRange(new Box(-.5,0,50,.5,2,51),new Vec3d(0,1.6,0)),"Distant indicator suppressed");
        int one=BowDamageEstimate.color(6,6), two=BowDamageEstimate.color(12,6), four=BowDamageEstimate.color(24,6);
        check(one==0xFF2818 && ((one>>8)&255)<((two>>8)&255) && ((two>>8)&255)<((four>>8)&255),"Shot spectrum not red to orange");
    }
    private static void phantomEyes() {
        near(PhantomCrosshair.centerY(100,13) + 13 * 26f / (2 * 64),96,"Phantom wing tips are too far above crosshair");
        near(PhantomCrosshair.width(.65f), 11.7f, "Default Phantom has not been reduced");
        near(PhantomCrosshair.width(5), 22, "Large saved scale escaped crosshair cap");
        var state=new PhantomCrosshair.State();long now=10_000_000_000L;
        state.update(true,false,12,0,now);state.update(true,false,12,0,now+200_000_000);
        near(state.eyes(),0,"Circling Phantom eyes are not white");
        state.update(true,true,12,0,now+400_000_000);check(state.eyes()>.8,"Dive eyes do not become green");
        state.update(true,false,12,0,now+600_000_000);state.update(true,false,12,0,now+900_000_000);
        near(state.alpha(now+900_000_000),1,"Takeoff/circling hid a nearby Phantom");
        state.update(false,false,12,0,now+900_000_000);
        check(state.eyes()<.01,"Lost focus retained green eyes");
        near(state.alpha(now+1_500_000_000),0,"Lost focus did not fade");
        var approach = new PhantomCrosshair.State();
        approach.update(true,true,24,0,now);
        approach.update(true,true,2,0,now+1_000_000_000);
        check(approach.fold()>.99,"Close dive did not fold wings");
        approach.update(true,true,2,now+1_000_000_000,now+1_000_000_000);
        near(approach.alpha(now+1_000_000_000),.6f,"Hit did not soften icon");
        approach.update(true,false,2,now+1_000_000_000,now+1_800_000_000);
        check(approach.fold()<.002&&approach.eyes()<.002,"Hit did not reopen wings and whiten eyes");
        near(approach.alpha(now+1_800_000_000),1,"Hit recovery did not finish");
        approach.update(false,false,2,now+1_000_000_000,now+1_800_000_000);
        near(approach.alpha(now+2_400_000_000L),0,"Dead/blocked Phantom reappeared after hit");
        check(PhantomHudMath.fold(24)==0&&PhantomHudMath.fold(2)==1
                &&PhantomHudMath.fold(12)>PhantomHudMath.fold(18),"Wing proximity ordering wrong");
    }
    private static void safeSettings() throws Exception {
        var config=SixthSenseConfig.decode("{\"raidPos\":null,\"bowPos\":{\"scale\":1e39,\"anchorX\":2,\"x\":-999},\"phantomScale\":-5,\"warningOpacity\":5,\"enableDragonWarning\":true}");
        check(config.raidPos!=null&&config.bowPos.scale==.7f&&config.bowPos.anchorX==null&&config.bowPos.x==-1,"Invalid saved settings not repaired");
        check(config.phantomScale==.1f&&config.warningOpacity==1,"Config limits ignored");
        Path dir=Files.createTempDirectory("sixthsense-config-check-");Path path=dir.resolve("settings.json");
        java.util.function.Predicate<String> valid=text->{try{SixthSenseConfig.decode(text);return true;}catch(RuntimeException e){return false;}};
        try {
            ConfigFiles.write(path,"{\"showGhastDistance\":false}",valid);
            ConfigFiles.write(path,"{\"showGhastDistance\":true}",valid);
            check(!SixthSenseConfig.decode(Files.readString(ConfigFiles.backup(path))).showGhastDistance,"Backup did not retain previous settings");
            try { ConfigFiles.write(path,"{invalid",valid);throw new AssertionError("Malformed save accepted"); }
            catch(java.io.IOException expected) { }
            check(SixthSenseConfig.decode(Files.readString(path)).showGhastDistance,"Rejected write changed settings");
            Files.writeString(path,"broken");ConfigFiles.write(path,"{}",valid);
            check(!SixthSenseConfig.decode(Files.readString(ConfigFiles.backup(path))).showGhastDistance,"Malformed file overwrote valid backup");
        } finally {
            try(var files=Files.list(dir)){for(Path file:files.toList())Files.delete(file);}Files.delete(dir);
        }
    }
    private static void near(float a,float b,String message){check(Math.abs(a-b)<.001,message);}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}

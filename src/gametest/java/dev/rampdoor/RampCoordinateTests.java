package dev.rampdoor;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.permissions.PermissionSet;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class RampCoordinateTests {
    private static void check(boolean value,String message) { if(!value) throw new IllegalStateException(message); }
    private static void rejected(Runnable action) {
        try { action.run(); } catch(IllegalArgumentException expected) { return; }
        throw new IllegalStateException("Invalid coordinates were accepted");
    }
    private static int command(GameTestHelper h,CommandSourceStack source,String command) {
        try { return h.getLevel().getServer().getCommands().getDispatcher().execute(command,source); }
        catch(Exception e) { throw new IllegalStateException(command,e); }
    }
    private static String xyz(BlockPos p) { return p.getX()+" "+p.getY()+" "+p.getZ(); }
    private static void collisionIntact(GameTestHelper h,RampDefinition r) {
        check(!r.collision.isEmpty(),"Open collision disappeared");
        for(var p:r.collision) check(h.getLevel().getBlockState(p.block()).is(RampDoorMod.COLLISION),"Another ramp removed collision at "+p);
    }
    @GameTest(structure="fabric-gametest-api-v1:empty",maxTicks=1000)
    public void coordinatesAndMultipleRamps(GameTestHelper h) {
        RampManager m=RampDoorMod.manager(h.getLevel().getServer());
        BlockPos p=h.absolutePos(new BlockPos(-256,160,-256));
        // Keep both ramps inside shared chunks, rather than testing only isolated far-away ramps.
        p=new BlockPos(Math.floorDiv(p.getX(),16)*16+1,p.getY(),Math.floorDiv(p.getZ(),16)*16+1);
        for(int x=(p.getX()-16)>>4;x<=(p.getX()+32)>>4;x++)
            for(int z=(p.getZ()-16)>>4;z<=(p.getZ()+32)>>4;z++)h.getLevel().getChunk(x,z);
        var south=RampCoordinates.layout(p,p.offset(3,0,0),p.offset(0,0,5));
        var north=RampCoordinates.layout(p,p.offset(3,0,0),p.offset(0,0,-5));
        var east=RampCoordinates.layout(p,p.offset(0,0,3),p.offset(5,0,0));
        var west=RampCoordinates.layout(p,p.offset(0,0,3),p.offset(-5,0,0));
        for(var layout:List.of(south,north,east,west)) {
            check(layout.width()==4 && layout.length()==6,"Inclusive dimensions incorrect");
            double hingeEdge="x".equals(layout.axis())?layout.hinge().z:layout.hinge().x;
            int firstRow="x".equals(layout.axis())?layout.first().getZ():layout.first().getX();
            check(hingeEdge==firstRow+(layout.extend()<0?1:0),"Negative direction hinge edge incorrect");
            check(RampCoordinates.layout(layout.second(),layout.first(),layout.front()).hinge().equals(layout.hinge()),"Reversed hinge endpoints changed geometry");
        }
        BlockPos fixed=p;
        rejected(() -> RampCoordinates.layout(fixed,fixed.offset(3,0,2),fixed.offset(0,0,5)));
        rejected(() -> RampCoordinates.layout(fixed,fixed.offset(3,1,0),fixed.offset(0,0,5)));
        rejected(() -> RampCoordinates.layout(fixed,fixed.offset(300,0,0),fixed.offset(0,0,5)));

        String id="coord_"+UUID.randomUUID().toString().replace("-","");
        var source=h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel())
            .withPermission(PermissionSet.ALL_PERMISSIONS).withPosition(Vec3.atLowerCornerOf(p));
        check(command(h,source,"ramp demo "+id+" ~ ~ ~ ~3 ~ ~ ~ ~ ~5")==1,"Relative-coordinate generation failed");
        RampDefinition a=m.get(id);
        BlockPos p2=p.offset(6,0,0);
        check(command(h,source,"ramp demo "+id+" "+xyz(p2)+" "+xyz(p2.offset(3,0,0))+" "+xyz(p2.offset(0,0,5)))==1,"Second ramp generation failed");
        RampDefinition b=m.get(id+"_2");
        check(a!=b && !a.displayTag().equals(b.displayTag()),"Duplicate name reused first ramp");
        check(a.blocks().keySet().stream().noneMatch(b.blocks()::containsKey),"Generated ramp footprints overlap");
        int blocks=a.template.blocks.size();
        check(command(h,source,"ramp capture "+id+" "+xyz(p.offset(3,0,5))+" "+xyz(p.offset(0,-1,0)))==1,"Coordinate capture failed");
        check(a.template.blocks.size()==blocks,"Coordinate capture changed block count");

        var hingeSource=source.withPosition(new Vec3(a.hingeX,a.hingeY,a.hingeZ));
        check(command(h,hingeSource,"ramp direction "+id+" ~ ~-4 ~8")==1,"Coordinate direction failed");
        check(a.axis.equals("x") && a.extend==1 && a.direction.equals("down") && !a.autoAxis,"Incorrect direction mapping");
        check(Math.abs(a.angle-Math.toDegrees(Math.atan2(4,8)))<1e-8,"Coordinate angle incorrect");
        var before=a.blocks(); var angle=a.angle;
        rejected(() -> RampCoordinates.heading(a,new Vec3(a.hingeX,a.hingeY-4,a.hingeZ)));
        rejected(() -> RampCoordinates.heading(a,new Vec3(a.hingeX+8,a.hingeY,a.hingeZ+8)));
        check(command(h,hingeSource,"ramp direction "+id+" ~ ~ ~8")==1 && a.angle==angle,"Same Y must preserve slope");
        check(before.equals(a.blocks()),"Direction setting resized or moved real blocks");
        try {
            var saved=new RampPersistentState(m.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT));
            check(saved.data.ramps.containsKey(b.id) && saved.data.ramps.get(id).angle==angle,"Multiple ramp settings were not persisted");
        } catch(java.io.IOException e) { throw new IllegalStateException(e); }
        check(command(h,source,"ramp create "+id)==1,"Duplicate /create was rejected");
        check(m.get(id+"_3").template.blocks.isEmpty(),"Duplicate /create overwrote an existing template");
        m.delete(m.get(id+"_3"));
        a.duration=2;b.duration=2;m.save();
        m.move(a,RampState.OPEN);m.move(b,RampState.OPEN);
        h.runAfterDelay(9,() -> {
            check(a.state==RampState.OPEN && b.state==RampState.OPEN,"Two ramps did not open together");
            collisionIntact(h,a);collisionIntact(h,b);
            check(m.displayCount(a)==a.template.blocks.size() && m.displayCount(b)==b.template.blocks.size(),"Displays mixed across ramps");
            check(command(h,hingeSource,"ramp direction "+id+" ~ ~4 ~8")==0,"Open geometry was edited in place");
            // A stale or changed predicted surface may touch a neighbour: cleanup must not erase it.
            GridPos borrowed=b.collision.getFirst(); a.collision.add(borrowed);
            m.recover(a);collisionIntact(h,b);
            m.move(a,RampState.CLOSED);
            h.runAfterDelay(9,() -> {
                check(a.state==RampState.CLOSED && b.state==RampState.OPEN,"Independent close changed neighbour state");
                collisionIntact(h,b);m.delete(a);collisionIntact(h,b);
                m.move(b,RampState.CLOSED);
                h.runAfterDelay(9,() -> { check(b.state==RampState.CLOSED,"Second close failed");m.delete(b);h.succeed(); });
            });
        });
    }
}

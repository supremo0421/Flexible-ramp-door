package dev.rampdoor;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.coordinates.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.phys.*;
import static net.minecraft.commands.Commands.*;

public final class RampCommands {
    @FunctionalInterface private interface Action { String run(CommandContext<CommandSourceStack> context) throws Exception; }
    private static int run(CommandContext<CommandSourceStack> c, Action a) {
        try { String message = a.run(c); c.getSource().sendSuccess(() -> Component.literal(message), false); return 1; }
        catch (Exception e) { c.getSource().sendFailure(Component.literal(e.getMessage() == null ? e.toString() : e.getMessage())); return 0; }
    }
    private static RampManager manager(CommandContext<CommandSourceStack> c) {
        RampManager m = RampDoorMod.manager(c.getSource().getServer());
        if (m == null) throw new IllegalArgumentException("RampDoor is not ready."); return m;
    }
    private static RampDefinition ramp(CommandContext<CommandSourceStack> c) { return manager(c).get(StringArgumentType.getString(c, "id")); }
    private static RampDefinition editable(CommandContext<CommandSourceStack> c) {
        RampDefinition r = ramp(c); manager(c).editable(r); return r;
    }
    private static RampDefinition coordinateEditable(CommandContext<CommandSourceStack> c) {
        var r=editable(c);
        if(r.state!=RampState.CLOSED) throw new IllegalArgumentException("Close the ramp before changing coordinate settings.");
        if(!r.dimension.equals(c.getSource().getLevel().dimension().identifier().toString()))
            throw new IllegalArgumentException("Run coordinate commands in the ramp's dimension (or use /execute in).");
        return r;
    }
    private static RequiredArgumentBuilder<CommandSourceStack, String> id() {
        return Commands.<String>argument("id", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(manager(c).storage.data.ramps.keySet(), b));
    }
    private static BlockPos pointed(CommandSourceStack source) throws Exception {
        var player = source.getPlayerOrException();
        HitResult hit = player.pick(64, 0, false);
        return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block.getBlockPos() : player.blockPosition();
    }
    private static String select(CommandContext<CommandSourceStack> c, boolean first, BlockPos pos) throws Exception {
        manager(c).select(c.getSource().getPlayerOrException().getUUID(), c.getSource().getLevel(), pos, first);
        return (first ? "Pos1: " : "Pos2: ") + GridPos.of(pos);
    }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = literal("ramp").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
        root.then(literal("create").then(id().executes(c -> run(c, x -> {
            var r=manager(c).create(StringArgumentType.getString(c, "id"), c.getSource().getLevel(), BlockPos.containing(c.getSource().getPosition()));
            return "Ramp '" + r.id + "' created. Select pos1/pos2, then /ramp capture "+r.id+".";
        }))));
        root.then(literal("delete").then(id().executes(c -> run(c, x -> { manager(c).delete(ramp(c)); return "Ramp deleted. Its blocks remain in the world."; }))));
        root.then(literal("list").executes(c -> run(c, x -> "Ramps: " + String.join(", ", manager(c).storage.data.ramps.keySet()))));
        root.then(literal("info").then(id().executes(c -> run(c, x -> {
            var r = ramp(c);
            return r.id + ": " + r.state + ", " + r.dimension + ", blocks=" + r.template.blocks.size()
                + ", hinge=" + r.hingeX + "," + r.hingeY + "," + r.hingeZ + ", axis=" + r.axis + ", extend=" + r.extend
                + ", direction=" + r.direction + ", angle=" + r.angle + ", ticks=" + r.duration + ", easing=" + r.easing
                + ", collision=" + r.collision.size() + ", controller=" + r.controller;
        }))));
        root.then(literal("debug").then(id().executes(c -> run(c, x -> manager(c).debug(ramp(c))))));
        for (boolean first : new boolean[]{true, false}) {
            root.then(literal(first ? "pos1" : "pos2").executes(c -> run(c, x -> select(c, first, pointed(c.getSource()))))
                .then(argument("pos", BlockPosArgument.blockPos()).executes(c -> run(c, x -> select(c, first, BlockPosArgument.getBlockPos(c, "pos"))))));
        }
        // v0.2 captures one RAMP_TEMPLATE: the closed structure. The open shape is that template rotated.
        root.then(literal("capture").then(id().executes(c -> run(c, x -> {
            var r = ramp(c);
            int blocks = manager(c).capture(r, c.getSource().getPlayerOrException().getUUID());
            return "Ramp template captured: " + blocks + " blocks. Axis " + r.axis + ", extending "
                + (r.extend > 0 ? "+" : "-") + RampGeometry.extensionAxis(r).getSerializedName() + ".";
        })).then(argument("from",BlockPosArgument.blockPos()).then(argument("to",BlockPosArgument.blockPos()).executes(c -> run(c,x -> {
            var r=coordinateEditable(c); BlockPos from=BlockPosArgument.getBlockPos(c,"from"),to=BlockPosArgument.getBlockPos(c,"to");
            int count=manager(c).capture(r,from,to);
            return "Captured "+count+" blocks in "+(Math.abs(from.getX()-to.getX())+1)+" x "+(Math.abs(from.getY()-to.getY())+1)+" x "+(Math.abs(from.getZ()-to.getZ())+1)+" selection (X/Y/Z).";
        }))))));
        root.then(literal("hinge").then(id().executes(c -> run(c, x -> {
            BlockPos p = pointed(c.getSource()); var r = editable(c); r.hinge(p.getX(), p.getY(), p.getZ()); manager(c).save(); return "Ramp hinge set to " + GridPos.of(p);
        })).then(argument("pos", Vec3Argument.vec3(false)).executes(c -> run(c, x -> {
            Vec3 p = Vec3Argument.getVec3(c, "pos"); var r = editable(c); r.hinge(p.x, p.y, p.z); manager(c).save(); return "Ramp hinge set to " + p;
        })))));
        root.then(literal("angle").then(id().then(argument("degrees", DoubleArgumentType.doubleArg(10, 90)).executes(c -> run(c, x -> {
            var r = editable(c); r.angle = DoubleArgumentType.getDouble(c, "degrees"); manager(c).save();
            return "Open angle: " + r.angle + (RampCollisionSurface.walkable(r) ? "" : " (too steep for a walking surface)");
        })))));
        root.then(literal("duration").then(id().then(argument("ticks", IntegerArgumentType.integer(2, 1200)).executes(c -> run(c, x -> {
            var r = editable(c); r.duration = IntegerArgumentType.getInteger(c, "ticks"); manager(c).save(); return "Duration: " + r.duration + " ticks";
        })))));
        var axis = id();
        for (String a : new String[]{"x", "z"}) axis.then(literal(a).executes(c -> run(c, x -> {
            var r = editable(c); r.axis = a; r.autoAxis = false; manager(c).save(); return "Hinge axis: " + a + " (auto-orientation off)";
        })));
        root.then(literal("axis").then(axis));
        var extend = id();
        for (String sign : new String[]{"positive", "negative"}) extend.then(literal(sign).executes(c -> run(c, x -> {
            var r = editable(c); r.extend = sign.equals("positive") ? 1 : -1; r.autoAxis = false; manager(c).save();
            return "Deck extends " + (r.extend > 0 ? "+" : "-") + RampGeometry.extensionAxis(r).getSerializedName() + " from the hinge.";
        })));
        root.then(literal("extend").then(extend));
        var direction = id();
        for (String d : new String[]{"down", "up"}) direction.then(literal(d).executes(c -> run(c, x -> {
            var r = editable(c); r.direction = d; manager(c).save(); return "Opening direction: " + d;
        })));
        direction.then(argument("toward",Vec3Argument.vec3(false)).executes(c -> run(c,x -> {
            var r=coordinateEditable(c);var heading=RampCoordinates.heading(r,Vec3Argument.getVec3(c,"toward"));
            heading.apply(r);manager(c).save();
            return "Direction: "+(r.extend>0?"+":"-")+RampGeometry.extensionAxis(r).getSerializedName()+", "+r.direction+", angle="+String.format(java.util.Locale.ROOT,"%.2f",r.angle)+" degrees; hinge axis="+r.axis+". Target is a direction ray; template size unchanged.";
        })));
        root.then(literal("direction").then(direction));
        var easing = id();
        for (String e : new String[]{"ease_in_out", "linear"}) easing.then(literal(e).executes(c -> run(c, x -> {
            var r = editable(c); r.easing = e; manager(c).save(); return "Interpolation curve: " + e;
        })));
        root.then(literal("easing").then(easing));
        var collision = id();
        collision.then(literal("auto").executes(c -> run(c, x -> {
            var r = editable(c); r.collisionMode = "auto"; r.collisionWidth = 0; r.collisionLength = 0; manager(c).save();
            return "Collision surface: auto (" + manager(c).collisionPlan(r).size() + " blocks planned)";
        })));
        for (String field : new String[]{"width", "length"}) {
            collision.then(literal(field).then(argument("blocks", IntegerArgumentType.integer(1, 256)).executes(c -> run(c, x -> {
                var r = editable(c); int value = IntegerArgumentType.getInteger(c, "blocks");
                r.collisionMode = "manual";
                if (field.equals("width")) r.collisionWidth = value; else r.collisionLength = value;
                manager(c).save();
                return "Collision " + field + ": " + value + " (" + manager(c).collisionPlan(r).size() + " blocks planned)";
            }))));
        }
        root.then(literal("collision").then(collision));
        root.then(literal("bind").then(id().executes(c -> run(c, x -> {
            var r = editable(c); manager(c).pendingBindings.put(c.getSource().getPlayerOrException().getUUID(), r.id); return "Right-click a Ramp Controller to bind " + r.id + ".";
        }))));
        root.then(literal("open").then(id().executes(c -> run(c, x -> manager(c).move(ramp(c), RampState.OPEN)))));
        root.then(literal("close").then(id().executes(c -> run(c, x -> manager(c).move(ramp(c), RampState.CLOSED)))));
        root.then(literal("toggle").then(id().executes(c -> run(c, x -> manager(c).toggle(ramp(c))))));
        for (String name : new String[]{"cleanup", "recover"}) {
            root.then(literal(name).then(id().executes(c -> run(c, x -> {
                var r = ramp(c); manager(c).recover(r);
                return "Cleanup complete: " + r.id + " is " + r.state + " with " + manager(c).displayCount(r)
                    + " displays and " + r.collision.size() + " collision blocks.";
            }))));
        }
        root.then(literal("demo").executes(c -> run(c,x -> {
            var r=RampDemo.create(manager(c),"ramp",c.getSource().getLevel(),BlockPos.containing(c.getSource().getPosition()));
            return "Created '"+r.id+"' (16 x 22). /ramp open "+r.id;
        })).then(id().executes(c -> run(c, x -> {
            var r=RampDemo.create(manager(c), StringArgumentType.getString(c, "id"), c.getSource().getLevel(), BlockPos.containing(c.getSource().getPosition()));
            return "436-block cargo ramp created at your position; hinge on the deck's top face, opening 32 degrees down. /ramp open "
                + r.id;
        })).then(argument("hingeStart",BlockPosArgument.blockPos()).then(argument("hingeEnd",BlockPosArgument.blockPos()).then(argument("front",BlockPosArgument.blockPos()).executes(c -> run(c,x -> {
            var layout=RampCoordinates.layout(BlockPosArgument.getBlockPos(c,"hingeStart"),BlockPosArgument.getBlockPos(c,"hingeEnd"),BlockPosArgument.getBlockPos(c,"front"));
            var r=RampDemo.create(manager(c),StringArgumentType.getString(c,"id"),c.getSource().getLevel(),layout);
            return "Created '"+r.id+"': width="+layout.width()+", length="+layout.length()+", blocks="+r.template.blocks.size()+", extends "+(r.extend>0?"+":"-")+RampGeometry.extensionAxis(r).getSerializedName()+". /ramp open "+r.id;
        })))))));
        dispatcher.register(root);
    }
}

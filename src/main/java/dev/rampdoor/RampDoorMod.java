package dev.rampdoor;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.network.chat.Component;
import org.slf4j.*;
import java.util.*;

public final class RampDoorMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("rampdoor");
    public static RampControllerBlock CONTROLLER;
    public static RampCollisionBlock COLLISION;
    public static BlockEntityType<RampControllerBlockEntity> CONTROLLER_ENTITY;
    public static TicketType TICKET;
    private static final Map<MinecraftServer, RampManager> MANAGERS = new IdentityHashMap<>();
    public static RampManager manager(MinecraftServer server) { return MANAGERS.get(server); }
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath("rampdoor", path); }
    @Override public void onInitialize() {
        RampConfig config = RampConfig.load();
        var blockId = ResourceKey.create(Registries.BLOCK, id("ramp_controller"));
        CONTROLLER = Registry.register(BuiltInRegistries.BLOCK, blockId, new RampControllerBlock(BlockBehaviour.Properties.of().strength(3.5f).setId(blockId)));
        var collisionId = ResourceKey.create(Registries.BLOCK, id("ramp_collision"));
        // No BlockItem on purpose: the collision surface is never obtainable, placeable or dropped.
        COLLISION = Registry.register(BuiltInRegistries.BLOCK, collisionId, new RampCollisionBlock(
            BlockBehaviour.Properties.of().strength(-1.0f, 3600000.0f).noLootTable().noOcclusion()
                .noTerrainParticles().pushReaction(PushReaction.BLOCK)
                .isViewBlocking((state, level, pos) -> false).isSuffocating((state, level, pos) -> false)
                .setId(collisionId)));
        var itemId = ResourceKey.create(Registries.ITEM, id("ramp_controller"));
        Registry.register(BuiltInRegistries.ITEM, itemId, new BlockItem(CONTROLLER, new Item.Properties().setId(itemId).useBlockDescriptionPrefix()));
        CONTROLLER_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id("ramp_controller"), FabricBlockEntityTypeBuilder.create(RampControllerBlockEntity::new, CONTROLLER).build());
        TICKET = Registry.register(BuiltInRegistries.TICKET_TYPE, id("animation"), new TicketType(0, TicketType.FLAG_LOADING | TicketType.FLAG_SIMULATION));
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> RampCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                RampManager manager = new RampManager(server, config); MANAGERS.put(server, manager); manager.recoverAll();
            } catch (Exception e) { throw new IllegalStateException("RampDoor save could not be loaded; refusing to discard data", e); }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> { var m = manager(server); if (m != null) m.tick(); });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> { var m = manager(server); if (m != null) m.stop(); });
        ServerLifecycleEvents.SERVER_STOPPED.register(MANAGERS::remove);
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (!entity.entityTags().contains(RampDisplayManager.TAG)) return;
            var m = manager(level.getServer());
            if (m == null || entity.entityTags().stream().noneMatch(m::ownsDisplay)) entity.discard();
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.getBlockState(hit.getBlockPos()).is(CONTROLLER)) return InteractionResult.PASS;
            if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.SUCCESS;
            if (player.isSpectator()) return InteractionResult.PASS;
            var m = manager(sl.getServer()); if (m == null) return InteractionResult.FAIL;
            try {
                String binding = m.pendingBindings.get(player.getUUID());
                if (binding != null) {
                    if (!((ServerPlayer)player).createCommandSourceStack().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        throw new IllegalArgumentException("Binding requires OP level 2.");
                    RampDefinition r = m.get(binding); m.editable(r);
                    if (!r.dimension.equals(sl.dimension().identifier().toString())) throw new IllegalArgumentException("Controller must be in the ramp dimension.");
                    GridPos p = GridPos.of(hit.getBlockPos());
                    for (var other : m.storage.data.ramps.values()) {
                        if (other != r && other.dimension.equals(r.dimension) && p.equals(other.controller)) throw new IllegalArgumentException("Controller already bound to " + other.id);
                    }
                    r.controller = p; m.save(); m.pendingBindings.remove(player.getUUID());
                    ((ServerPlayer)player).sendSystemMessage(Component.literal("Controller bound to " + r.id + "."), false);
                } else if (!player.isShiftKeyDown()) {
                    RampDefinition r = m.storage.data.ramps.values().stream().filter(a -> GridPos.of(hit.getBlockPos()).equals(a.controller) && a.dimension.equals(sl.dimension().identifier().toString())).findFirst().orElseThrow(() -> new IllegalArgumentException("Controller is unbound; use /ramp bind <id>."));
                    ((ServerPlayer)player).sendSystemMessage(Component.literal(m.toggle(r)), true);
                }
            } catch (RuntimeException e) { ((ServerPlayer)player).sendSystemMessage(Component.literal(e.getMessage()), false); }
            return InteractionResult.SUCCESS;
        });
    }
}

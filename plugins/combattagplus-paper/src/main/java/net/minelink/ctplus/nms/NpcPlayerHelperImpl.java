package net.minelink.ctplus.nms;

import com.google.common.collect.Lists;
import com.mojang.datafixers.util.Pair;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.PlayerDataStorage;
import net.minelink.ctplus.compat.base.NpcIdentity;
import net.minelink.ctplus.compat.base.NpcPlayerHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

public class NpcPlayerHelperImpl implements NpcPlayerHelper {

    @Override
    public Player spawn(Player player) {
        NpcPlayer npcPlayer = NpcPlayer.valueOf(player);
        ServerLevel worldServer = ((CraftWorld) player.getWorld()).getHandle();
        Location l = player.getLocation();

        npcPlayer.spawnIn(worldServer);
        npcPlayer.forceSetPositionRotation(l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
        npcPlayer.gameMode.setLevel(worldServer);
        npcPlayer.invulnerableTime = 0;

        for (ServerPlayer serverPlayer : MinecraftServer.getServer().getPlayerList().getPlayers()) {
            if (serverPlayer instanceof NpcPlayer) continue;

            ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, npcPlayer);
            serverPlayer.connection.send(packet);
        }

        worldServer.addFreshEntity(npcPlayer);

        return npcPlayer.getBukkitEntity();
    }

    @Override
    public void despawn(Player player) {
        ServerPlayer entity = ((CraftPlayer) player).getHandle();
        if (!(entity instanceof NpcPlayer)) {
            throw new IllegalArgumentException();
        }

        for (ServerPlayer serverPlayer : MinecraftServer.getServer().getPlayerList().getPlayers()) {
            if (serverPlayer instanceof NpcPlayer) continue;

            ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(List.of(entity.getUUID()));
            serverPlayer.connection.send(packet);
        }

        ServerLevel worldServer = entity.serverLevel();
        worldServer.chunkSource.removeEntity(entity);
        worldServer.getPlayers(serverPlayer -> serverPlayer instanceof NpcPlayer).remove(entity);
        removePlayerList(player);
    }

    @Override
    public boolean isNpc(Player player) {
        return ((CraftPlayer) player).getHandle() instanceof NpcPlayer;
    }

    @Override
    public NpcIdentity getIdentity(Player player) {
        if (!isNpc(player)) {
            throw new IllegalArgumentException();
        }

        return ((NpcPlayer) ((CraftPlayer) player).getHandle()).getNpcIdentity();
    }

    @Override
    public void updateEquipment(Player player) {
        ServerPlayer entity = ((CraftPlayer) player).getHandle();

        if (!(entity instanceof NpcPlayer)) {
            throw new IllegalArgumentException();
        }

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack item = entity.getItemBySlot(slot);
            if (item.getItem() == Items.AIR) continue;

            AttributeMap attributemapbase = entity.getAttributes();

            item.forEachModifier(slot, (holder, attributemodifier) -> {
                AttributeInstance attributemodifiable = attributemapbase.getInstance(holder);

                if (attributemodifiable != null) {
                    attributemodifiable.removeModifier(attributemodifier.id());
                    attributemodifiable.addTransientModifier(attributemodifier);
                }
            });

            // This is also called by super.tick(), but the flag this.bx is not public
            List<Pair<EquipmentSlot, ItemStack>> list = Lists.newArrayList();
            list.add(Pair.of(slot, item));
            Packet<ClientGamePacketListener> packet = new ClientboundSetEquipmentPacket(entity.getId(), list);
            entity.serverLevel().chunkSource.broadcast(entity, packet);
        }
    }

    @Override
    public void syncOffline(Player player) {
        ServerPlayer entity = ((CraftPlayer) player).getHandle();

        if (!(entity instanceof NpcPlayer npcPlayer)) {
            throw new IllegalArgumentException();
        }

        NpcIdentity identity = npcPlayer.getNpcIdentity();
        Player p = Bukkit.getPlayer(identity.getId());
        if (p != null && p.isOnline()) return;

        PlayerDataStorage worldStorage = ((CraftWorld) Bukkit.getWorlds().getFirst()).getHandle().getServer().playerDataStorage;
        CompoundTag playerNbt = worldStorage.load(identity.getName(), identity.getId().toString()).orElse(null);

        // foodTickTimer is now private in 1.8.3 -- still private in 1.12 -- still private in 1.20.6
        Field foodTickTimerField;
        int foodTickTimer;
        try {
            //Although we can use Mojang mappings when developing, We need to use the obfuscated field name
            //until we can run a full Mojmapped server. I personally used this site when updating to 1.20.6:
            // https://mappings.cephx.dev/1.20.6/net/minecraft/world/food/FoodData.html
            foodTickTimerField = FoodData.class.getDeclaredField("d"); // todo fix
            foodTickTimerField.setAccessible(true);
            foodTickTimer = foodTickTimerField.getInt(entity.getFoodData());
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        playerNbt.putShort("Air", (short) entity.getAirSupply());
        // Health is now just a float; fractional is not stored separately. (1.12)
        playerNbt.putFloat("Health", entity.getHealth());
        playerNbt.putFloat("AbsorptionAmount", entity.getAbsorptionAmount());
        playerNbt.putInt("XpTotal", entity.experienceLevel);
        playerNbt.putInt("foodLevel", entity.getFoodData().getFoodLevel());
        playerNbt.putInt("foodTickTimer", foodTickTimer);
        playerNbt.putFloat("foodSaturationLevel", entity.getFoodData().getSaturationLevel());
        playerNbt.putFloat("foodExhaustionLevel", entity.getFoodData().exhaustionLevel);
        playerNbt.putShort("Fire", (short) entity.getRemainingFireTicks());
        playerNbt.put("Inventory", npcPlayer.getInventory().save(new ListTag()));

        File file1 = new File(worldStorage.getPlayerDir(), identity.getId() + ".dat.tmp");
        File file2 = new File(worldStorage.getPlayerDir(), identity.getId() + ".dat");

        try {
            NbtIo.writeCompressed(playerNbt, new FileOutputStream(file1));
        } catch (IOException e) {
            throw new RuntimeException("Failed to save player data for " + identity.getName(), e);
        }

        if ((!file2.exists() || file2.delete()) && !file1.renameTo(file2)) {
            throw new RuntimeException("Failed to save player data for " + identity.getName());
        }
    }

    @Override
    public void createPlayerList(Player player) {
        ServerPlayer p = ((CraftPlayer) player).getHandle();

        for (ServerPlayer serverPlayer : MinecraftServer.getServer().getPlayerList().getPlayers()) {
            ClientboundPlayerInfoUpdatePacket packet = new ClientboundPlayerInfoUpdatePacket(
                ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, serverPlayer);
            p.connection.send(packet);
        }
    }

    @Override
    public void removePlayerList(Player player) {
        ServerPlayer p = ((CraftPlayer) player).getHandle();
        for (ServerPlayer serverPlayer : MinecraftServer.getServer().getPlayerList().getPlayers()) {
            ClientboundPlayerInfoRemovePacket packet = new ClientboundPlayerInfoRemovePacket(List.of(serverPlayer.getUUID()));
            p.connection.send(packet);
        }
    }
}

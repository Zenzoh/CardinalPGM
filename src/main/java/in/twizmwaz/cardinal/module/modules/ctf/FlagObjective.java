package in.twizmwaz.cardinal.module.modules.ctf;

import in.twizmwaz.cardinal.GameHandler;
import in.twizmwaz.cardinal.chat.ChatConstant;
import in.twizmwaz.cardinal.chat.LocalizedChatMessage;
import in.twizmwaz.cardinal.event.MatchEndEvent;
import in.twizmwaz.cardinal.event.PlayerChangeTeamEvent;
import in.twizmwaz.cardinal.event.flag.FlagCaptureEvent;
import in.twizmwaz.cardinal.event.flag.FlagDropEvent;
import in.twizmwaz.cardinal.event.flag.FlagPickupEvent;
import in.twizmwaz.cardinal.event.flag.FlagRespawnEvent;
import in.twizmwaz.cardinal.module.GameObjective;
import in.twizmwaz.cardinal.module.TaskedModule;
import in.twizmwaz.cardinal.module.modules.ctf.net.Net;
import in.twizmwaz.cardinal.module.modules.ctf.post.Post;
import in.twizmwaz.cardinal.module.modules.filter.FilterModule;
import in.twizmwaz.cardinal.module.modules.filter.FilterState;
import in.twizmwaz.cardinal.module.modules.kit.KitNode;
import in.twizmwaz.cardinal.module.modules.proximity.GameObjectiveProximityHandler;
import in.twizmwaz.cardinal.module.modules.regions.RegionModule;
import in.twizmwaz.cardinal.module.modules.regions.type.PointRegion;
import in.twizmwaz.cardinal.module.modules.score.ScoreModule;
import in.twizmwaz.cardinal.module.modules.scoreboard.GameObjectiveScoreboardHandler;
import in.twizmwaz.cardinal.module.modules.scoreboard.ScoreboardModule;
import in.twizmwaz.cardinal.module.modules.team.TeamModule;
import in.twizmwaz.cardinal.module.modules.timeLimit.TimeLimit;
import in.twizmwaz.cardinal.util.ChatUtil;
import in.twizmwaz.cardinal.util.Fireworks;
import in.twizmwaz.cardinal.util.Flags;
import in.twizmwaz.cardinal.util.MiscUtil;
import in.twizmwaz.cardinal.util.PacketUtils;
import in.twizmwaz.cardinal.util.Teams;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.minecraft.server.EnumParticle;
import net.minecraft.server.PacketPlayOutWorldParticles;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Banner;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;

import java.util.Map;
import java.util.Set;

public class FlagObjective implements TaskedModule, GameObjective {

    private String id;
    private boolean show;
    private boolean required;
    private String name;
    private DyeColor color;
    private ChatColor chatColor;
    private Post post;                  // required
    private TeamModule owner;
    private boolean shared;             // Default: false
    private String carryMessage;
    private int points;                 // Default: 0
    private int pointsRate;             // Default: 0
    private FilterModule pickupFilter;
    private FilterModule dropFilter;
    private FilterModule captureFilter;
    private KitNode pickupKit;
    private KitNode dropKit;
    private KitNode carryKit;
    private boolean dropOnWater;
    private boolean beam;

    private FlagState state;
    private Banner banner;
    private ItemStack bannerItem;
    private Player picker;
    private Block currentFlagBlock;
    private ItemStack pickerHelmet;
    private double respawnTime = 0;
    private double recoverTime = 0;
    private ArmorStand armorStand;
    private Long lastUpdate = 0L;
    private Net lastNet = null;

    private FilterModule respawnFilter = null;
    private String respawnMessage = null;

    private GameObjectiveScoreboardHandler scoreboardHandler;
    private Map<String, GameObjectiveProximityHandler> flagProximityHandlers;
    private Map<String, GameObjectiveProximityHandler> netProximityHandlers;
    private boolean touched;
    private boolean complete;
    private boolean canRespawn;

    public FlagObjective(String id,
                         boolean required,
                         String name,
                         DyeColor color,
                         ChatColor chatColor,
                         boolean show,
                         Post post,
                         TeamModule owner,
                         boolean shared,
                         String carryMessage,
                         int points,
                         int pointsRate,
                         FilterModule pickupFilter,
                         FilterModule dropFilter,
                         FilterModule captureFilter,
                         KitNode pickupKit,
                         KitNode dropKit,
                         KitNode carryKit,
                         boolean dropOnWater,
                         boolean beam,
                         Set<Net> nets,
                         Map<String, GameObjectiveProximityHandler> flagProximityHandlers,
                         Map<String, GameObjectiveProximityHandler> netProximityHandlers) {
        this.id = id;
        this.required = required;
        this.name = name;
        this.color = color;
        this.chatColor = chatColor;
        this.show = show;
        this.post = post;
        this.owner = owner;
        this.shared = shared;
        if (shared) this.required = false;
        this.carryMessage = carryMessage;
        this.points = points;
        this.pointsRate = pointsRate;
        this.pickupFilter = pickupFilter;
        this.dropFilter = dropFilter;
        this.captureFilter = captureFilter;
        this.pickupKit = pickupKit;
        this.dropKit = dropKit;
        this.carryKit = carryKit;
        this.dropOnWater = dropOnWater;
        this.beam = beam;
        for (RegionModule region : post.getRegions()) {
            if (region.getCenterBlock().getBlock().getState() instanceof Banner) {
                this.banner = (Banner) region.getCenterBlock().getBlock().getState();
            }
        }
        bannerItem = new ItemStack(Material.BANNER);
        BannerMeta meta = (BannerMeta) bannerItem.getItemMeta();
        meta.setBaseColor(banner.getBaseColor());
        meta.setPatterns(banner.getPatterns());
        bannerItem.setItemMeta(meta);

        this.flagProximityHandlers = flagProximityHandlers;
        for (GameObjectiveProximityHandler proximityHandler : flagProximityHandlers.values()) {
            proximityHandler.setObjective(this);
        }
        this.netProximityHandlers = netProximityHandlers;
        for (GameObjectiveProximityHandler proximityHandler : netProximityHandlers.values()) {
            proximityHandler.setObjective(this);
        }

        currentFlagBlock = getPost().getInitialBlock();
        state = FlagState.POST;
        scoreboardHandler = new GameObjectiveScoreboardHandler(this);

        for (Net net : nets) {
            net.setFlag(this);
        }

        for (Entity entity : GameHandler.getGameHandler().getMatchWorld().getEntities()) {
            if (entity instanceof ArmorStand && entity.getName().equals(getDisplayName()) && entity.getLocation().toVector().equals(getCurrentFlagLocation().add(0,0.6875,0).toVector())) {
                entity.remove();
            }
        }

        updateArmorStand();

        if (this.owner == null) {
            int i = 0;
            for (TeamModule team : Teams.getTeams()) {
                if (!team.isObserver() && !pickupFilter.evaluate(team).equals(FilterState.ALLOW)) {
                    this.owner = team;
                    i++;
                }
            }
            if (i != 1) this.owner = null;
        }
        this.canRespawn = true;
    }

    @Override
    public void unload() {
        HandlerList.unregisterAll(this);
    }

    @Override
    public TeamModule getTeam() {
        return owner;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isTouched() {
        return touched;
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    @Override
    public boolean showOnScoreboard() {
        return show;
    }

    @Override
    public boolean isRequired() {
        return required;
    }

    @Override
    public GameObjectiveScoreboardHandler getScoreboardHandler() {
        return scoreboardHandler;
    }

    @Override
    public GameObjectiveProximityHandler getProximityHandler(TeamModule team) {
        return isTouched() ? netProximityHandlers.get(team.getId()) : flagProximityHandlers.get(team.getId());
    }

    public String getDisplayName() {
        return chatColor + getName();
    }

    public ChatColor getChatColor() {
        return chatColor;
    }

    public Post getPost() {
        return post;
    }

    public Player getPicker() {
        return picker;
    }

    public void setPicker(Player picker) {
        this.picker = picker;
    }

    public boolean isOnPost() {
        return state.equals(FlagState.POST);
    }

    public boolean isCarried() {
        return state.equals(FlagState.CARRIED);
    }

    public boolean isDropped() {
        return state.equals(FlagState.DROPPED) || state.equals(FlagState.DROPPED_ON_WATER);
    }

    public boolean isWaitingToRespawn() {
        return state.equals(FlagState.WAITING_TO_RESPAWN);
    }

    public boolean isRespawning() {
        return state.equals(FlagState.RESPAWNING);
    }

    public int getPoints() {
        return points;
    }

    public int getRespawnTime() {
        return (int)(respawnTime + 0.99D);
    }

    public int getRecoverTime() {
        return (int)(recoverTime + 0.99);
    }

    public boolean isShared() {
        return shared;
    }

    public Block getCurrentFlagBlock() {
        return currentFlagBlock;
    }

    public Location getCurrentFlagLocation() {
        return currentFlagBlock.getLocation().add(0.5,0,0.5);
    }

    public FilterModule getPickupFilter() {
        return pickupFilter;
    }

    public FilterModule getCaptureFilter() {
        return captureFilter;
    }

    public void setCanRespawn(boolean state) {
        this.canRespawn = state;
        setLastUpdate();
        tickAndUpdate();
    }

    public void setLastNet(Net net) {
        this.lastNet = net;
    }

    public void setRespawnFilter(FilterModule filter) {
        this.respawnFilter = filter;
    }

    public void setRespawnMessage(String respawnMessage) {
        this.respawnMessage = respawnMessage;
    }

    public Double getProximity(TeamModule team) {
        if (team.isObserver()) return flagProximityHandlers.size() == 1 ?
                (isTouched() ? netProximityHandlers.values().iterator().next().getProximity() : flagProximityHandlers.values().iterator().next().getProximity()) : null;
        if (team.equals(this.owner)) return null;
        return isTouched() ? netProximityHandlers.get(team.getId()).getProximity() : flagProximityHandlers.get(team.getId()).getProximity();
    }
    
    public boolean showProximity(TeamModule team) {
        return GameHandler.getGameHandler().getMatch().getModules().getModule(TimeLimit.class).getTimeLimit() != 0 &&
                ((team.isObserver() && flagProximityHandlers.size() == 1) || flagProximityHandlers.containsKey(team.getId()));
    }

    private Block nextSpawn () {
        RegionModule spawn = getPost().getNextFlagSpawn();
        Block block;
        if (spawn instanceof PointRegion) {
            block = ((PointRegion) spawn).getBlock();
        } else {
            block = spawn.getRandomPoint().getBlock();
        }
        return block;
    }

    public void respawnFlag() {
        if (GameHandler.getGameHandler().getMatch().isRunning()) {
            state = FlagState.POST;
            spawnFlag();
            FlagRespawnEvent e = new FlagRespawnEvent(this, getPost(), currentFlagBlock);
            this.complete = false;
            this.respawnFilter = null;
            this.respawnMessage = null;
            Bukkit.getServer().getPluginManager().callEvent(e);
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(new LocalizedChatMessage(ChatConstant.UI_FLAG_RESPAWNED, getDisplayName() + ChatColor.RESET).getMessage(p.getLocale()));
            }
        }
    }

    private void spawnFlag() {
        currentFlagBlock.setType(banner.getMaterial());
        Banner newBanner = (Banner)currentFlagBlock.getState();
        newBanner.setPatterns(banner.getPatterns());
        newBanner.setBaseColor(banner.getBaseColor());
        armorStand = createArmorStand();
        if (!isOnPost()) {
            if (currentFlagBlock.getRelative(BlockFace.DOWN).getType().equals(Material.STATIONARY_WATER)) {
                currentFlagBlock.getRelative(BlockFace.DOWN).setType(Material.ICE);
                state = FlagState.DROPPED_ON_WATER;
            } else {
                state = FlagState.DROPPED;
            }
            Flags.setBannerFacing(Flags.yawToFace(picker.getLocation().getYaw()), newBanner);
            FlagDropEvent event = new FlagDropEvent(getPicker(), this);
            Bukkit.getPluginManager().callEvent(event);
        } else {
            Flags.setBannerFacing(getPost().getYaw(), newBanner);
        }
        Player oldPicker = picker;
        this.lastNet = null;
        resetPlayer();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (oldPicker != null && player.equals(oldPicker)) continue;
            getPost().tryPickupFlag(player, player.getLocation(), null, this);
            if (picker != null) break;
        }
    }

    private ArmorStand createArmorStand() {
        Location loc = getCurrentFlagLocation().add(0,0.6875,0);
        ArmorStand armorStand = GameHandler.getGameHandler().getMatchWorld().spawn(loc, ArmorStand.class);
        armorStand.setGravity(false);
        armorStand.setVisible(false);
        armorStand.setSmall(true);
        armorStand.setBasePlate(false);
        armorStand.setCustomNameVisible(true);
        armorStand.setCustomName(getDisplayName());
        return armorStand;
    }

    private void updateArmorStand() {
        if (armorStand == null || armorStand.isDead()) armorStand = createArmorStand();
        String suffix = isDropped() && GameHandler.getGameHandler().getMatch().isRunning() ? ChatColor.AQUA + " " + getRecoverTime() : "";
        armorStand.setCustomName(getDisplayName() + suffix);
    }

    private boolean validatePosition(Block block) {
        return ((block.getRelative(BlockFace.DOWN).getType().isSolid() || (dropOnWater && block.getRelative(BlockFace.DOWN).getType().equals(Material.STATIONARY_WATER))) &&  block.isEmpty() && block.getRelative(BlockFace.UP).isEmpty());
    }

    public void sendActionBar(boolean set) {
        if (picker == null) return;
        if (set) picker.sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(carryMessage));
        else picker.sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
    }

    private void resetPlayer() {
        if (picker != null) {
            sendActionBar(false);
            picker.resetTitle();
            picker.getInventory().setHelmet(pickerHelmet);
            if (dropKit != null) dropKit.apply(picker, null);
            if (carryKit != null) carryKit.remove(picker);
            pickerHelmet = null;
            picker = null;
        }

    }

    private void updateScoreboard() {
        if (isDropped() || isOnPost()) updateArmorStand();
        for (ScoreboardModule scoreboard : GameHandler.getGameHandler().getMatch().getModules().getModules(ScoreboardModule.class)) {
            scoreboard.updateObjectivePrefix(this);
        }
    }

    private void sendParticles() {
        if (!beam) return;
        Location loc = picker != null ? picker.getLocation() : getCurrentFlagLocation();
        loc.add(0, 56.0, 0);
        PacketPlayOutWorldParticles packet = new PacketPlayOutWorldParticles(EnumParticle.ITEM_CRACK, true, (float)loc.getX(), (float)loc.getY(), (float)loc.getZ(), 0.15f, 24.0f, 0.15f, 0.0f, 40, 35, (int)color.getWoolData());
        PacketUtils.broadcastPacket(packet);
    }

    public boolean inRange(Location newLoc, Location oldLoc) {
        Location flag = getCurrentFlagLocation().clone();
        Double xDiff = (newLoc.getX() - flag.getX()) < 0 ? (newLoc.getX() - flag.getX()) *-1 : newLoc.getX() - flag.getX();
        Double yDiff = (newLoc.getY() - flag.getY()) < 0 ? (newLoc.getY() - flag.getY()) *-1 : newLoc.getY() - flag.getY();
        Double zDiff = (newLoc.getZ() - flag.getZ()) < 0 ? (newLoc.getZ() - flag.getZ()) *-1 : newLoc.getZ() - flag.getZ();
        if (yDiff < 1.0D && ((xDiff < 1.0D && zDiff < 0.7D) || (xDiff < 0.7D && zDiff < 1.0D))) {
            if (oldLoc == null) return true;
            Location newY0 = newLoc.clone();
            Location oldY0 = oldLoc.clone();
            newY0.setY(0);
            oldY0.setY(0);
            flag.setY(0);
            Double dist1 = newY0.distance(flag);
            Double dist2 = oldY0.distance(flag);
            return dist1 < dist2;
        }
        return false;
    }

    @EventHandler
    public void onPlayerPickupFlag(FlagPickupEvent event) {
        if (event.getFlag().equals(this)) {
            this.touched = true;
            if (pickupKit != null) pickupKit.apply(event.getPlayer(), null);
            if (carryKit != null) carryKit.apply(event.getPlayer(), null);
            pickerHelmet = event.getPlayer().getInventory().getHelmet();
            event.getPlayer().getInventory().setHelmet(bannerItem);
            currentFlagBlock.setType(Material.AIR);
            if (state.equals(FlagState.DROPPED_ON_WATER)) currentFlagBlock.getRelative(BlockFace.DOWN).setType(Material.STATIONARY_WATER);
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage(new LocalizedChatMessage(ChatConstant.UI_FLAG_PICKED_UP, getDisplayName() + ChatColor.RESET, Teams.getTeamByPlayer(event.getPlayer()).get().getColor() + event.getPlayer().getName()).getMessage(p.getLocale()));
            }
            Fireworks.spawnFlagFirework(getCurrentFlagLocation(), getCurrentFlagLocation().getWorld(), MiscUtil.convertChatColorToColor(chatColor));
            setPicker(event.getPlayer());
            state = FlagState.CARRIED;
            armorStand.remove();
            sendActionBar(true);
            event.getPlayer().showTitle(new TextComponent(""), new TextComponent(carryMessage), 0, 20, 10);
        }
    }

    @EventHandler
    public void onDropFlag(FlagDropEvent event) {
        if (!event.getFlag().equals(this)) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(new LocalizedChatMessage(ChatConstant.UI_FLAG_DROPPED, getDisplayName() + ChatColor.RESET).getMessage(p.getLocale()));
        }
        recoverTime = getPost().getRecoverTime();
        setLastUpdate();
        tickAndUpdate();
    }

    @EventHandler
    public void onCaptureFlag(FlagCaptureEvent event) {
        if (!event.getFlag().equals(this)) return;
        if (event.getNet().getPost() != null) post = event.getNet().getPost();
        state = canRespawn ? FlagState.RESPAWNING : FlagState.WAITING_TO_RESPAWN;
        this.complete = true;
        this.touched = false;
        this.lastNet = null;
        currentFlagBlock = nextSpawn();
        respawnTime = getPost().getRespawnTime(event.getPlayer().getLocation(), currentFlagBlock.getLocation());
        resetPlayer();
        setLastUpdate();
        tickAndUpdate();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!(event.getPlayer().getHealth() > 0) || !GameHandler.getGameHandler().getMatch().isRunning()) return;
        Player player = event.getPlayer();
        if (event.getFrom().getBlock() != event.getTo().getBlock() && picker != null && player.equals(picker)) {
            if (validatePosition(event.getTo().getBlock())) {
                currentFlagBlock = event.getTo().getBlock();
            } else if (event.getTo().getBlock().getType().equals(Material.STATIONARY_WATER) && validatePosition(event.getTo().getBlock().getRelative(BlockFace.UP))) {
                currentFlagBlock = event.getTo().getBlock().getRelative(BlockFace.UP);
            }
        }
        if (!event.getFrom().getBlock().equals(currentFlagBlock)) getPost().tryPickupFlag(player, event.getTo(), event.getFrom(), this);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (event.getBlock().equals(currentFlagBlock) && block.getType().equals(banner.getMaterial()) && block.getState().equals(banner)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + new LocalizedChatMessage(ChatConstant.ERROR_BREAK_FLAG).getMessage(event.getPlayer().getLocale()));
        }
        block = event.getBlock().getRelative(BlockFace.UP);
        if (event.getBlock().equals(currentFlagBlock) && block.getType().equals(banner.getMaterial()) && block.getState().equals(banner)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + new LocalizedChatMessage(ChatConstant.ERROR_BREAK_BLOCK_UNDER_FLAG).getMessage(event.getPlayer().getLocale()));
        }
    }

    @EventHandler
    public void onDead(PlayerDeathEvent event) {
        if (getPicker() != null && event.getEntity().equals(getPicker())) {
            spawnFlag();
        }
    }

    @EventHandler
    public void onTeamChange(PlayerChangeTeamEvent event) {
        if (getPicker() != null && event.getPlayer().equals(getPicker())) {
            spawnFlag();
        }
    }

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        if (getPicker() != null && event.getPlayer().equals(getPicker())) {
            spawnFlag();
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player p = (Player) event.getWhoClicked();
            if (getPicker() != null && getPicker().equals(p) && event.getCurrentItem() != null && event.getCurrentItem().equals(bannerItem)) {
                event.setCancelled(true);
                spawnFlag();
            }
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (getPicker() != null && getPicker().equals(event.getPlayer()) && event.getItemDrop().getItemStack().equals(bannerItem)) {
            event.getItemDrop().remove();
            spawnFlag();
        }
    }

    @EventHandler
    public void onItemSpawn(ItemSpawnEvent event) {
        if (event.getEntity().getItemStack().equals(bannerItem)) {
            event.getEntity().remove();
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onMatchEnd(MatchEndEvent event) {
        if (isCarried()) {
            spawnFlag();
        }
        tickAndUpdate();
    }


    @Override
    public void run() {
        sendParticles();
        if (GameHandler.getGameHandler().getMatch().isRunning()) {
            if (System.currentTimeMillis() - lastUpdate > 100) {
                tickAndUpdate();
            }
        }
    }

    private void tickAndUpdate() {
        updateFlags();
        updateScoreboard();
    }

    private void setLastUpdate() {
        lastUpdate = System.currentTimeMillis();
    }

    private void updateFlags() {
        double diff = (double)(System.currentTimeMillis() - lastUpdate) / 1000;
        setLastUpdate();

        if (isOnPost() && getPost().getPointsRate() != 0 && post.getOwner() != null) {
            addPoints(post.getOwner() , (double)getPost().getPointsRate() * diff);
        }

        if (isCarried() && pointsRate != 0) {
            addPoints(owner != null ? owner : Teams.getTeamByPlayer(picker).get(), (double) pointsRate * diff);
        }

        if (isCarried() && lastNet != null) {
            lastNet.tryCapture(this);
        }

        if (isDropped()) {
            if (recoverTime > 0) {
                double max = Math.min(recoverTime, diff);
                recoverTime -= max;
                diff -= max;
            }
            if (recoverTime == 0) {
                currentFlagBlock.setType(Material.AIR);
                if (state.equals(FlagState.DROPPED_ON_WATER)) currentFlagBlock.getRelative(BlockFace.DOWN).setType(Material.STATIONARY_WATER);
                Location oldBlock = currentFlagBlock.getLocation();
                armorStand.remove();
                currentFlagBlock = nextSpawn();
                state = FlagState.WAITING_TO_RESPAWN;
                respawnTime = getPost().getRespawnTime(oldBlock, currentFlagBlock.getLocation());
            }
        }

        if (isWaitingToRespawn() && canRespawn) {
            state = FlagState.RESPAWNING;
        }

        if (isRespawning()) {
            if (respawnTime > 0) {
                double max = Math.min(respawnTime, diff);
                respawnTime -= max;
            }
            if (respawnTime == 0) {
                if ((respawnFilter == null || !respawnFilter.evaluate().equals(FilterState.DENY))) {
                    respawnFlag();
                } else if (respawnMessage != null) {
                    ChatUtil.getGlobalChannel().sendMessage(respawnMessage);
                    respawnMessage = null;
                }
            }

        }
        sendActionBar(true);
    }

    private void addPoints(TeamModule team, double points) {
        for (ScoreModule score : GameHandler.getGameHandler().getMatch().getModules().getModules(ScoreModule.class)) {
            if (team.equals(score.getTeam())) {
                score.addScore(points);
            }
        }
    }

    public enum FlagState {
        POST,
        CARRIED,
        DROPPED,
        DROPPED_ON_WATER,
        WAITING_TO_RESPAWN,
        RESPAWNING;
    }
}

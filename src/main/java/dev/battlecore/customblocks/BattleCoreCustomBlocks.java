package dev.battlecore.customblocks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.NoteBlock;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class BattleCoreCustomBlocks extends JavaPlugin implements CommandExecutor, Listener {
    private static final int[] MENU_SLOTS = {11, 12, 13, 14, 15};

    private final List<BlockDefinition> blocks = List.of(
        new BlockDefinition("flesh_growth", "Flesh Growth", Material.NOTE_BLOCK, 20),
        new BlockDefinition("rusted_vent_grate", "Rusted Vent Grate", Material.NOTE_BLOCK, 21),
        new BlockDefinition("containment_glass", "Containment Glass", Material.NOTE_BLOCK, 22),
        new BlockDefinition("quarantine_door", "Quarantine Door", Material.NOTE_BLOCK, 23),
        new BlockDefinition("decayed_wood", "Decayed Wood", Material.NOTE_BLOCK, 24)
    );
    private final Map<String, BlockDefinition> byId = new HashMap<>();
    private final Map<Integer, BlockDefinition> byNote = new HashMap<>();
    private NamespacedKey blockIdKey;
    private boolean packEnabled;
    private String packUrl;
    private byte[] packHash;
    private boolean packRequired;
    private String packPrompt;

    @Override
    public void onEnable() {
        blockIdKey = new NamespacedKey(this, "block_id");
        for (BlockDefinition block : blocks) {
            byId.put(block.id(), block);
            byNote.put(block.note(), block);
        }
        saveDefaultConfig();
        loadPackSettings();
        getCommand("customblock").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("BattleCore Custom Blocks enabled with " + blocks.size() + " blocks.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command is for players only.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("battlecorecustomblocks.use")) {
            player.sendMessage(Component.text("You do not have permission to use custom blocks.", NamedTextColor.RED));
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("pack")) {
            sendResourcePack(player);
            return true;
        }
        openMenu(player);
        return true;
    }

    private void loadPackSettings() {
        FileConfiguration config = getConfig();
        packEnabled = config.getBoolean("resource-pack.enabled", false);
        packUrl = config.getString("resource-pack.url", "");
        packRequired = config.getBoolean("resource-pack.required", false);
        packPrompt = config.getString("resource-pack.prompt", "BattleCore Custom Blocks");
        String sha1 = config.getString("resource-pack.sha1", "");
        packHash = null;
        if (sha1 != null && sha1.matches("[0-9a-fA-F]{40}")) {
            packHash = new byte[20];
            for (int i = 0; i < 20; i++) {
                packHash[i] = (byte) Integer.parseInt(sha1.substring(i * 2, i * 2 + 2), 16);
            }
        }
        if (packEnabled && (packUrl == null || packUrl.isBlank() || packHash == null)) {
            packEnabled = false;
            getLogger().warning("Resource-pack delivery disabled: set a valid URL and SHA-1 in config.yml.");
        }
    }

    private void openMenu(Player player) {
        CustomBlockMenu menu = new CustomBlockMenu();
        Inventory inventory = Bukkit.createInventory(
            menu, 54, Component.text("BattleCore Custom Blocks", NamedTextColor.DARK_RED)
        );
        menu.setInventory(inventory);
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.text(" "));
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
        for (int i = 0; i < blocks.size(); i++) {
            inventory.setItem(MENU_SLOTS[i], createBlockItem(blocks.get(i)));
        }
        player.openInventory(inventory);
    }

    private ItemStack createBlockItem(BlockDefinition block) {
        ItemStack item = new ItemStack(block.icon());
        ItemMeta meta = item.getItemMeta();
        meta.setItemModel(new NamespacedKey("battlecore", block.id()));
        meta.displayName(Component.text(block.name(), NamedTextColor.GOLD));
        meta.lore(List.of(
            Component.text("Click to select, then right-click a block face.", NamedTextColor.GRAY),
            Component.text("Requires the BattleCore resource pack.", NamedTextColor.DARK_GRAY)
        ));
        meta.getPersistentDataContainer().set(blockIdKey, PersistentDataType.STRING, block.id());
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMenuClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof CustomBlockMenu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getCurrentItem() == null) {
            return;
        }
        ItemMeta meta = event.getCurrentItem().getItemMeta();
        if (meta == null) {
            return;
        }
        String id = meta.getPersistentDataContainer().get(blockIdKey, PersistentDataType.STRING);
        BlockDefinition block = byId.get(id);
        if (block == null) {
            return;
        }
        ItemStack selected = createBlockItem(block);
        Map<Integer, ItemStack> leftovers = new HashMap<>(player.getInventory().addItem(selected));
        if (!leftovers.isEmpty()) {
            player.sendMessage(Component.text("Your inventory is full.", NamedTextColor.RED));
            return;
        }
        player.closeInventory();
        player.sendMessage(Component.text(
            "Selected " + block.name() + ". Right-click a block face to place it.", NamedTextColor.GREEN
        ));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            return;
        }
        ItemStack item = event.getItem();
        if (item != null && item.getType() == Material.NOTE_BLOCK) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String id = meta.getPersistentDataContainer().get(blockIdKey, PersistentDataType.STRING);
                BlockDefinition block = byId.get(id);
                if (block != null) {
                    event.setCancelled(true);
                    event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
                    event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
                    Block target = clicked.getRelative(event.getBlockFace());
                    if ((!target.isEmpty() && target.getType() != Material.WATER)
                        || !target.getWorld().getWorldBorder().isInside(target.getLocation())) {
                        playerCannotPlace(event.getPlayer());
                        return;
                    }
                    target.setType(Material.NOTE_BLOCK, false);
                    NoteBlock data = (NoteBlock) target.getBlockData();
                    data.setInstrument(Instrument.PIANO);
                    data.setNote(new Note(block.note()));
                    data.setPowered(false);
                    target.setBlockData(data, false);
                    if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                        item.setAmount(item.getAmount() - 1);
                        event.getPlayer().getInventory().setItem(event.getHand(), item);
                    }
                    return;
                }
            }
        }
        if (clicked.getType() == Material.NOTE_BLOCK
            && clicked.getBlockData() instanceof NoteBlock noteBlock
            && byNote.containsKey(unsigned(noteBlock.getNote().getId()))) {
            event.setCancelled(true);
            event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        }
    }

    private void playerCannotPlace(Player player) {
        player.sendMessage(Component.text("That space is blocked or outside the world border.", NamedTextColor.RED));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.NOTE_BLOCK || !(block.getBlockData() instanceof NoteBlock noteBlock)) {
            return;
        }
        BlockDefinition definition = byNote.get(unsigned(noteBlock.getNote().getId()));
        if (definition == null) {
            return;
        }
        event.setDropItems(false);
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), createBlockItem(definition));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (packEnabled) {
            sendResourcePack(event.getPlayer());
        }
    }

    private void sendResourcePack(Player player) {
        if (!packEnabled) {
            player.sendMessage(Component.text("The custom block resource pack is not configured yet.", NamedTextColor.YELLOW));
            return;
        }
        try {
            player.setResourcePack(packUrl, packHash, packPrompt, packRequired);
            player.sendMessage(Component.text("BattleCore Custom Blocks pack download requested.", NamedTextColor.GREEN));
        } catch (IllegalArgumentException exception) {
            player.sendMessage(Component.text("The custom block resource pack could not be sent.", NamedTextColor.RED));
            getLogger().warning("Resource-pack send failed: " + exception.getMessage());
        }
    }

    private static int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }
}

package org.dw363;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HubItemWind extends JavaPlugin implements Listener {

    private String itemName;
    private List<String> itemDescription;
    private Material itemMaterial;
    private int itemSlot;
    private String command;
    private Sound useSound;
    private final Map<Player, Long> lastUse = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfigValues();

        getServer().getPluginManager().registerEvents(this, this);

        getCommand("HubItemWind").setExecutor(this);
        getCommand("HubItemWind").setTabCompleter(new HubItemWindTabCompleter());
    }

    private void loadConfigValues() {
        FileConfiguration config = getConfig();
        itemName = translateHexColors(config.getString("item.name", "Special Item"));
        itemDescription = config.getStringList("item.description").stream()
                .map(this::translateHexColors)
                .collect(Collectors.toList());
        itemMaterial = Material.valueOf(config.getString("item.material", "DIAMOND"));
        itemSlot = config.getInt("item.slot", 1); // Слот по умолчанию - 1
        command = config.getString("item.command", "Server");

        String soundName = config.getString("item.sound", "ENTITY_ITEM_PICKUP"); // По умолчанию звук поднимания предмета
        try {
            useSound = Sound.valueOf(soundName);
        } catch (IllegalArgumentException e) {
            getLogger().warning("В config.yml указан неверный звук. Будет использован по умолчанию ENTITY_ITEM_PICKUP.");
            useSound = Sound.ENTITY_PLAYER_LEVELUP;
        }
    }

    private String translateHexColors(String message) {
        Pattern hexPattern = Pattern.compile("&#([A-Fa-f0-9]{6})");
        Matcher matcher = hexPattern.matcher(message);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder hexFormatted = new StringBuilder("§x");
            for (char ch : hex.toCharArray()) {
                hexFormatted.append("§").append(ch);
            }
            matcher.appendReplacement(buffer, hexFormatted.toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        giveItem(player);
    }

    private void giveItem(Player player) {
        ItemStack item = createItem();
        player.getInventory().setItem(itemSlot, item);
    }

    private ItemStack createItem() {
        ItemStack item = new ItemStack(itemMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(itemName);
            meta.setLore(itemDescription);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item != null && event.getAction().name().contains("RIGHT_CLICK") && isHubItem(item)) {
            long currentTime = System.currentTimeMillis();
            long lastUsed = lastUse.getOrDefault(player, 0L);

            long timePassed = currentTime - lastUsed;
            long cooldownTime = 2000; // 2000 миллисекунд = 40 тиков
            long timeRemaining = cooldownTime - timePassed;

            if (timeRemaining > 0) {
                int secondsRemaining = (int) Math.ceil(timeRemaining / 1000.0);

                String secondsWord = getSecondsWord(secondsRemaining);
                sendActionBar(player,ChatColor.RED + "Подождите " + secondsRemaining + " " + secondsWord + " перед использованием!");
            } else {
                lastUse.put(player, currentTime)    ;
                Bukkit.dispatchCommand(player, command);
                player.playSound(player.getLocation(), useSound, 1.0f, 1.0f);
            }

            event.setCancelled(true);
        }
    }

    private String getSecondsWord(int seconds) {
        if (seconds % 10 == 1 && seconds % 100 != 11) {
            return "секунду";
        } else if ((seconds % 10 >= 2 && seconds % 10 <= 4) && (seconds % 100 < 10 || seconds % 100 >= 20)) {
            return "секунды";
        } else {
            return "секунд";
        }
    }

    private void sendActionBar(Player player, String message) {
        new BukkitRunnable() {
            @Override
            public void run() {
                player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, net.md_5.bungee.api.chat.TextComponent.fromLegacyText(message));
            }
        }.runTask(this);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            ItemStack clickedItem = event.getCurrentItem();

            if (isHubItem(clickedItem)) {
                if (event.getSlot() != itemSlot) {
                    event.setCancelled(true);
                    Bukkit.getScheduler().runTaskLater(this, () -> player.getInventory().setItem(itemSlot, createItem()), 1L);
                } else {
                    event.setCancelled(true);
                }
            }
            removeDuplicateItems(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();

            if (isHubItem(event.getOldCursor()) || isHubItem(event.getCursor())) {
                event.setCancelled(true);
                Bukkit.getScheduler().runTaskLater(this, () -> player.getInventory().setItem(itemSlot, createItem()), 1L);
            }
            removeDuplicateItems(player);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();

        if (isHubItem(item)) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTaskLater(this, () -> event.getPlayer().getInventory().setItem(itemSlot, createItem()), 1L);
            removeDuplicateItems(event.getPlayer());
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            Bukkit.getScheduler().runTaskLater(this, () -> {
                ItemStack item = player.getInventory().getItem(itemSlot);
                if (!isHubItem(item)) {
                    player.getInventory().setItem(itemSlot, createItem());
                }
                removeDuplicateItems(player);
            }, 1L);
        }
    }

    private void removeDuplicateItems(Player player) {
        ItemStack correctItem = player.getInventory().getItem(itemSlot);
        if (!isHubItem(correctItem)) {
            return;
        }
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            if (i != itemSlot && isHubItem(player.getInventory().getItem(i))) {
                player.getInventory().clear(i);
            }
        }
    }

    private boolean isHubItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;
        String formattedItemName = ChatColor.translateAlternateColorCodes('&', itemName);
        String itemNameStripped = ChatColor.stripColor(meta.getDisplayName());
        String formattedItemNameStripped = ChatColor.stripColor(formattedItemName);
        return itemNameStripped.equals(formattedItemNameStripped) && item.getType() == itemMaterial;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("HubItemWind")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (sender.hasPermission("hubitemwind.reload")) {
                    reloadConfig();
                    loadConfigValues();
                    sender.sendMessage(ChatColor.GREEN + "Конфигурация перезагружена!");
                } else {
                    sender.sendMessage(ChatColor.RED + "Нет прав для выполнения данной команды!");
                }
                return true;
            }
        }
        return false;
    }

    private class HubItemWindTabCompleter implements TabCompleter {
        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            if (command.getName().equalsIgnoreCase("HubItemWind")) {
                List<String> completions = new ArrayList<>();
                if (args.length == 1) {
                    if ("reload".startsWith(args[0].toLowerCase())) {
                        completions.add("reload");
                    }
                }
                return completions;
            }
            return null;
        }
    }
}
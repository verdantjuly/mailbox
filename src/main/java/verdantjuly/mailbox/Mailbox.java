package verdantjuly.mailbox;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Mailbox extends JavaPlugin implements Listener {

    private static final String INVENTORY_TITLE = "우편 발송";
    private final Map<UUID, Location> mailboxes = new HashMap<>(); // Player UUID to mailbox location
    private final Map<Location, UUID> mailboxOwners = new HashMap<>(); // Mailbox location to owner UUID
    private final Map<Player, Player> pendingReceivers = new HashMap<>(); // Player to receiver mapping

    @Override
    public void onEnable() {
        Bukkit.getLogger().info("MailboxPlugin이 활성화되었습니다.");
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        Bukkit.getLogger().info("MailboxPlugin이 비활성화되었습니다.");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (command.getName().equalsIgnoreCase("우체통생성")) {
            if (sender instanceof Player player) {
                createMailbox(player);
                return true;
            } else {
                sender.sendMessage("플레이어만 이 명령어를 사용할 수 있습니다.");
                return true;
            }
        } else if (command.getName().equalsIgnoreCase("우편발송")) {
            if (sender instanceof Player player) {
                if (args.length == 1) {
                    Player receiver = Bukkit.getPlayer(args[0]);
                    // Check if the receiver is online or offline
                    UUID receiverUUID = Bukkit.getOfflinePlayer(args[0]).getUniqueId();
                    if (mailboxes.containsKey(receiverUUID)) {
                        openSendMailGUI(player, receiverUUID); // Open GUI with receiver's UUID
                    } else {
                        player.sendMessage("수신자를 찾을 수 없습니다. 수신자가 우체통을 생성해야 합니다.");
                    }
                } else {
                    player.sendMessage("사용법: /우편발송 <수신자>");
                }
                return true;
            } else {
                sender.sendMessage("플레이어만 이 명령어를 사용할 수 있습니다.");
                return true;
            }
        }
        return false;
    }

    private void createMailbox(Player player) {
        UUID playerUUID = player.getUniqueId();

        // Check if the player already has a mailbox
        if (mailboxes.containsKey(playerUUID)) {
            Location existingLocation = mailboxes.get(playerUUID);
            Block existingBlock = existingLocation.getBlock();

            // Ensure the existing mailbox is still a chest
            if (existingBlock.getType() == Material.CHEST) {
                player.sendMessage("이미 우체통이 존재합니다. 위치: "
                        + existingLocation.getBlockX() + ", "
                        + existingLocation.getBlockY() + ", "
                        + existingLocation.getBlockZ());
                return;  // Prevent duplicate mailbox creation
            } else {
                // If no chest exists at the old location, remove the mailbox info
                mailboxes.remove(playerUUID);
                mailboxOwners.remove(existingLocation); // Also remove the owner information
            }
        }

        // Create a new mailbox
        Location loc = player.getLocation().add(1, 0, 0);
        loc.getBlock().setType(Material.CHEST);
        Chest chest = (Chest) loc.getBlock().getState();
        chest.setCustomName(player.getName() + "님의 우체통");
        chest.update();

        mailboxes.put(playerUUID, loc);  // Store the new mailbox location
        mailboxOwners.put(loc, playerUUID); // Store the owner of the mailbox
        player.sendMessage("새 우체통이 생성되었습니다! 위치: "
                + loc.getBlockX() + ", "
                + loc.getBlockY() + ", "
                + loc.getBlockZ());
    }

    private void openSendMailGUI(Player player, UUID receiverUUID) {
        Inventory sendMailInventory = Bukkit.createInventory(null, 9, Component.text(INVENTORY_TITLE));
        pendingReceivers.put(player, Bukkit.getPlayer(receiverUUID)); // Store the receiver for later

        // Set up the send button
        ItemStack sendButton = new ItemStack(Material.ELYTRA);
        var sendMeta = sendButton.getItemMeta();
        if (sendMeta != null) {
            sendMeta.displayName(Component.text("전송"));
            sendButton.setItemMeta(sendMeta);
        }

        sendMailInventory.setItem(8, sendButton); // Place the send button in the last slot
        player.openInventory(sendMailInventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity entity = event.getWhoClicked();
        if (!(entity instanceof Player player)) return;

        // Check if the clicked inventory is the send mail GUI
        if (event.getView().title().equals(Component.text(INVENTORY_TITLE))) {
            // Handle the "Send" button click
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ELYTRA) {
                Player receiver = pendingReceivers.get(player);  // Get the receiver
                if (receiver == null) {
                    player.sendMessage("수신자를 찾을 수 없습니다.");
                    return;
                }

                UUID receiverUUID = receiver.getUniqueId();
                Location mailboxLocation = mailboxes.get(receiverUUID);  // Get receiver's mailbox location
                if (mailboxLocation == null) {
                    player.sendMessage(receiver.getName() + "님의 우체통이 존재하지 않아 전송할 수 없습니다.");
                    return;
                }

                // Get the receiver's mailbox chest and inventory
                Chest mailboxChest = (Chest) mailboxLocation.getBlock().getState();
                Inventory receiverInventory = mailboxChest.getInventory();
                List<String> itemList = new ArrayList<>();
                boolean allItemsTransferred = true;

                // Transfer items from the send mail GUI to the receiver's mailbox
                for (int i = 0; i < event.getClickedInventory().getSize(); i++) {
                    ItemStack item = event.getClickedInventory().getItem(i);
                    if (item != null && item.getType() != Material.ELYTRA) {
                        itemList.add(item.getType() + " x " + item.getAmount());
                        HashMap<Integer, ItemStack> remainingItems = receiverInventory.addItem(item); // Attempt to add items

                        // Handle insufficient space in the receiver's mailbox
                        if (!remainingItems.isEmpty()) {
                            allItemsTransferred = false;
                            player.sendMessage(receiver.getName() + "님의 우체통에 공간이 부족하여 일부 아이템이 전송되지 않았습니다.");
                            break;
                        }
                        event.getClickedInventory().clear(i);  // Clear item from the send mail GUI
                    }
                }

                // Create and add a letter if all items were transferred
                if (allItemsTransferred) {
                    ItemStack paper = new ItemStack(Material.PAPER);  // Create a letter
                    ItemMeta meta = paper.getItemMeta();
                    meta.setDisplayName("우체통 편지");
                    List<String> lore = new ArrayList<>();
                    lore.add("발송자: " + player.getName());
                    lore.add("내용:");
                    lore.addAll(itemList);  // Add the list of items sent
                    meta.setLore(lore);
                    paper.setItemMeta(meta);

                    receiverInventory.addItem(paper);  // Add the letter to the receiver's mailbox
                    player.sendMessage(receiver.getName() + "님의 우체통으로 아이템이 성공적으로 전송되었습니다!");
                }

                pendingReceivers.remove(player);  // Remove receiver info after sending
                player.closeInventory();  // Close the send mail GUI
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.hasBlock() && event.getClickedBlock().getType() == Material.CHEST) {
            Chest chest = (Chest) event.getClickedBlock().getState();
            Location chestLocation = chest.getLocation();

            // Check if this chest is a mailbox
            if (chest.getCustomName() != null && chest.getCustomName().contains("우체통")) {
                UUID ownerUUID = mailboxOwners.get(chestLocation); // Get the owner UUID
                if (ownerUUID != null && ownerUUID.equals(event.getPlayer().getUniqueId())) {
                    event.setCancelled(true);
                    event.getPlayer().openInventory(chest.getInventory());  // Open the mailbox inventory
                } else {
                    event.getPlayer().sendMessage("당신은 이 우체통을 열 수 없습니다.");
                }
            }
        }
    }
}
package verdantjuly.mailbox;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
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

public class Mailbox extends JavaPlugin implements Listener {

    private static final String INVENTORY_TITLE = "우편 발송";
    private final Map<Player, Location> mailboxes = new HashMap<>();
    private final Map<Player, Player> pendingReceivers = new HashMap<>();

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
            if (sender instanceof Player) {
                Player player = (Player) sender;
                createMailbox(player);
                return true;
            } else if (sender instanceof ConsoleCommandSender) {
                sender.sendMessage("플레이어만 이 명령어를 사용할 수 있습니다.");
                return true;
            }
        } else if (command.getName().equalsIgnoreCase("우편발송")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if (args.length == 1) {
                    Player receiver = Bukkit.getPlayer(args[0]);
                    if (receiver != null && receiver.isOnline()) {
                        openSendMailGUI(player, receiver);
                        pendingReceivers.put(player, receiver);  // 수신자 저장
                    } else {
                        player.sendMessage("수신자를 찾을 수 없습니다.");
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
        // 플레이어의 우체통이 이미 존재하는지 확인
        if (mailboxes.containsKey(player)) {
            Location existingLocation = mailboxes.get(player);
            Block existingBlock = existingLocation.getBlock();

            // 기존 우체통이 상자로 존재하는지 추가 확인
            if (existingBlock.getType() == Material.CHEST) {
                player.sendMessage("이미 우체통이 존재합니다. 위치: "
                        + existingLocation.getBlockX() + ", "
                        + existingLocation.getBlockY() + ", "
                        + existingLocation.getBlockZ());
                return;  // 중복 생성 방지
            } else {
                // 만약 기존 위치에 상자가 없다면 우체통 정보 제거
                mailboxes.remove(player);
            }
        }

        // 새 우체통 생성
        Location loc = player.getLocation().add(1, 0, 0);
        loc.getBlock().setType(Material.CHEST);
        Chest chest = (Chest) loc.getBlock().getState();
        chest.setCustomName(player.getName() + "님의 우체통");
        chest.update();

        mailboxes.put(player, loc);  // 새 우체통 위치 저장
        player.sendMessage("새 우체통이 생성되었습니다! 위치: "
                + loc.getBlockX() + ", "
                + loc.getBlockY() + ", "
                + loc.getBlockZ());
    }

    private void openSendMailGUI(Player player, Player receiver) {
        Inventory sendMailInventory = Bukkit.createInventory(null, 9, Component.text(INVENTORY_TITLE));

        // 전송 버튼 설정
        ItemStack sendButton = new ItemStack(Material.ELYTRA);
        var sendMeta = sendButton.getItemMeta();
        if (sendMeta != null) {
            sendMeta.displayName(Component.text("전송"));
            sendButton.setItemMeta(sendMeta);
        }

        sendMailInventory.setItem(8, sendButton); // 마지막 칸에 전송 버튼
        player.openInventory(sendMailInventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity entity = event.getWhoClicked();
        if (!(entity instanceof Player player)) return;

        // 발송 GUI 인벤토리인지 확인
        if (event.getView().title().equals(Component.text(INVENTORY_TITLE))) {

            // "전송" 버튼을 클릭했을 때
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ELYTRA) {
                Player receiver = pendingReceivers.get(player);  // 수신자 확인
                if (receiver == null) {
                    player.sendMessage("수신자를 찾을 수 없습니다.");
                    return;
                }

                Location mailboxLocation = mailboxes.get(receiver);  // 수신자 우체통 위치 확인
                if (mailboxLocation == null) {
                    player.sendMessage(receiver.getName() + "님의 우체통이 존재하지 않아 전송할 수 없습니다.");
                    return;
                }

                // 수신자 우체통 상자와 인벤토리 가져오기
                Chest mailboxChest = (Chest) mailboxLocation.getBlock().getState();
                Inventory receiverInventory = mailboxChest.getInventory();
                List<String> itemList = new ArrayList<>();
                boolean allItemsTransferred = true;

                // 발송 GUI의 마지막 줄(겉날개와 같은 줄) 아이템을 수신자 우체통으로 전송
                for (int i = 0; i < event.getClickedInventory().getSize(); i++) {
                        ItemStack item = event.getClickedInventory().getItem(i);
                        if (item != null && item.getType() != Material.ELYTRA) {
                            itemList.add(item.getType() + " x " + item.getAmount());
                            HashMap<Integer, ItemStack> remainingItems = receiverInventory.addItem(item); // 수신자 인벤토리에 아이템 추가 시도

                            // 수신자 우체통에 남은 공간이 부족할 경우 처리
                            if (!remainingItems.isEmpty()) {
                                allItemsTransferred = false;
                                player.sendMessage(receiver.getName() + "님의 우체통에 공간이 부족하여 일부 아이템이 전송되지 않았습니다.");
                                break;
                            }
                            event.getClickedInventory().clear(i);  // 발송 GUI 인벤토리에서 아이템 제거
                        }
                }

                // 종이 아이템 생성 및 추가
                if (allItemsTransferred) {
                    ItemStack paper = new ItemStack(Material.PAPER);  // 종이 아이템으로 편지 생성
                    ItemMeta meta = paper.getItemMeta();
                    meta.setDisplayName("우체통 편지");
                    List<String> lore = new ArrayList<>();
                    lore.add("발송자: " + player.getName());
                    lore.add("내용:");
                    lore.addAll(itemList);  // 발송된 아이템 목록을 내용으로 추가
                    meta.setLore(lore);
                    paper.setItemMeta(meta);

                    receiverInventory.addItem(paper);  // 수신자 우체통에 편지(종이)를 추가
                    player.sendMessage(receiver.getName() + "님의 우체통으로 아이템이 성공적으로 전송되었습니다!");
                }

                pendingReceivers.remove(player);  // 발송 완료 후 수신자 정보 제거
                player.closeInventory();  // 발송 GUI 닫기
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.hasBlock() && event.getClickedBlock().getType() == Material.CHEST) {
            Chest chest = (Chest) event.getClickedBlock().getState();
            if (chest.getCustomName() != null && chest.getCustomName().contains("우체통")) {
                event.setCancelled(true);
                event.getPlayer().openInventory(chest.getInventory());
            }
        }
    }
}

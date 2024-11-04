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
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Mailbox extends JavaPlugin implements Listener {

    private static final String INVENTORY_TITLE = "우체통 - 아이템 전송";
    private final Map<Player, Location> mailboxes = new HashMap<>();

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
        }
        return false;
    }

    private void createMailbox(Player player) {
        Location loc = player.getLocation().add(1, 0, 0);
        Block block = loc.getBlock();

        if (block.getType() != Material.CHEST) {
            block.setType(Material.CHEST);
            Chest chest = (Chest) block.getState();
            chest.setCustomName(player.getName() + "님의 우체통");
            chest.update();

            mailboxes.put(player, loc);  // 플레이어의 우체통 위치 저장
            player.sendMessage("우체통이 생성되었습니다!");
        } else {
            player.sendMessage("이 위치에 이미 우체통이 있습니다.");
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.hasBlock() && event.getClickedBlock().getType() == Material.CHEST) {
            Chest chest = (Chest) event.getClickedBlock().getState();
            if (chest.getCustomName() != null && chest.getCustomName().contains("우체통")) {
                event.setCancelled(true);
                openMailboxGUI(event.getPlayer());
            }
        }
    }

    private void openMailboxGUI(Player player) {
        Inventory mailboxInventory = Bukkit.createInventory(null, 9, Component.text(INVENTORY_TITLE));

        // 전송 버튼 설정
        ItemStack sendButton = new ItemStack(Material.ELYTRA);  // 겉날개로 설정
        sendButton.getItemMeta().displayName(Component.text("전송"));

        // GUI에 전송 버튼 배치
        mailboxInventory.setItem(8, sendButton); // 마지막 칸에 전송 버튼

        player.openInventory(mailboxInventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity entity = event.getWhoClicked();
        if (!(entity instanceof Player player)) return;

        Inventory inventory = event.getClickedInventory();
        if (inventory != null && event.getView().title().equals(Component.text(INVENTORY_TITLE))) {
            event.setCancelled(true);  // 아이템 이동 막기

            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ELYTRA) {
                // 전송 버튼을 클릭했을 때
                Player receiver = Bukkit.getPlayer("receiver"); // 수신자 지정 (예시: "receiver")
                if (receiver == null || !receiver.isOnline()) {
                    player.sendMessage("대상 플레이어를 찾을 수 없습니다.");
                    return;
                }

                Location mailboxLocation = mailboxes.get(receiver);
                if (mailboxLocation == null) {
                    // 수신자의 우체통이 없을 경우 전송 불가 메시지 출력
                    player.sendMessage(receiver.getName() + "님의 우체통이 존재하지 않아 전송할 수 없습니다.");
                    return;
                }

                Chest mailboxChest = (Chest) mailboxLocation.getBlock().getState();
                Inventory receiverInventory = mailboxChest.getInventory();
                List<String> itemList = new ArrayList<>();

                // 전송할 아이템을 수신자의 우체통으로 이동
                for (int i = 0; i < 8; i++) {  // 전송 버튼 제외한 첫 8칸의 아이템
                    ItemStack item = inventory.getItem(i);
                    if (item != null) {
                        itemList.add(item.getType() + " x " + item.getAmount());
                        receiverInventory.addItem(item);
                    }
                }

                // 편지 아이템 생성
                ItemStack letter = new ItemStack(Material.WRITABLE_BOOK);
                BookMeta meta = (BookMeta) letter.getItemMeta();
                meta.setTitle("우체통 편지");
                meta.setAuthor(player.getName());
                meta.addPage(String.join("\n", itemList));
                letter.setItemMeta(meta);

                // 수신자의 우체통에 편지 추가
                receiverInventory.addItem(letter);
                player.sendMessage(receiver.getName() + "님의 우체통으로 아이템을 성공적으로 전송했습니다!");

                entity.closeInventory();
            }
        }
    }
}


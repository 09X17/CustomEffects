package com.customeffects;

import java.util.List;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.customeffects.commands.EffectosCommand;
import com.customeffects.commands.FormatoCommand;
import com.customeffects.commands.FriendCommand;
import com.customeffects.database.DatabaseManager;
import com.customeffects.editor.RankEditorCommand;
import com.customeffects.editor.WebEditorServer;
import com.customeffects.listeners.FriendListener;
import com.customeffects.listeners.InventoryClickListener;
import com.customeffects.listeners.PlayerListener;
import com.customeffects.listeners.LuckPermsRankChangeListener;
import com.customeffects.listeners.VoucherListener;
import com.customeffects.menus.FriendMenuService;
import com.customeffects.placeholder.EffectosExpansion;
import com.customeffects.services.DataManager;
import com.customeffects.services.FriendService;
import com.customeffects.services.LuckPermsService;
import com.customeffects.services.MenuService;
import com.customeffects.services.VoucherService;
import com.customeffects.services.AnimatedPrefixService;

public final class CustomEffects extends JavaPlugin {
    private DatabaseManager database;
    private DataManager dataManager;
    private MenuService menuService;
    private VoucherService voucherService;
    private LuckPermsService luckPermsService;
    private FriendService friendService;
    private FriendMenuService friendMenuService;
    private FriendListener friendListener;
    private AnimatedPrefixService animatedPrefixService;
    private WebEditorServer webEditorServer;
    private LuckPermsRankChangeListener luckPermsRankChangeListener;

    public CustomEffects() {
    }

    @Override
    public void onEnable() {
        this.saveDefaultConfig();

        this.dataManager = new DataManager(this);
        this.dataManager.saveCategoryFiles();
        this.dataManager.updateConfigCommentsSafe();
        this.dataManager.updateCategoryFiles();
        this.dataManager.loadAll();

        this.database = new DatabaseManager();
        this.database.connect(this.getDataFolder(), this);

        this.luckPermsService = new LuckPermsService(this);
        this.voucherService = new VoucherService(this, dataManager);
        this.menuService = new MenuService(this, dataManager);
        this.friendService = new FriendService(this, dataManager);
        this.friendMenuService = new FriendMenuService(this, dataManager, friendService);
        this.friendListener = new FriendListener(this, friendService);
        this.animatedPrefixService = new AnimatedPrefixService(this, dataManager);
        this.animatedPrefixService.start();

        this.getServer().getPluginManager().registerEvents(new InventoryClickListener(this, menuService, dataManager), this);
        this.getServer().getPluginManager().registerEvents(new PlayerListener(this, voucherService), this);
        this.getServer().getPluginManager().registerEvents(new VoucherListener(this, dataManager, voucherService), this);
        this.getServer().getPluginManager().registerEvents(friendListener, this);

        EffectosCommand effectosCommand = new EffectosCommand(this, menuService, voucherService);
        var command = this.getCommand("effectos");
        if (command != null) {
            command.setExecutor(effectosCommand);
            command.setTabCompleter(effectosCommand);
        } else {
            this.getLogger().severe("El comando 'effectos' no está registrado en plugin.yml");
        }

        FormatoCommand formatoCommand = new FormatoCommand(this, menuService);
        var formatoCmd = this.getCommand("formato");
        if (formatoCmd != null) {
            formatoCmd.setExecutor(formatoCommand);
        } else {
            this.getLogger().severe("El comando 'formato' no está registrado en plugin.yml");
        }

        RankEditorCommand rankEditorCommand = new RankEditorCommand(this);
        var rankEditorCmd = this.getCommand("rankeditor");
        if (rankEditorCmd != null) {
            rankEditorCmd.setExecutor(rankEditorCommand);
            rankEditorCmd.setTabCompleter(rankEditorCommand);
        } else {
            this.getLogger().severe("El comando 'rankeditor' no está registrado en plugin.yml");
        }

        FriendCommand friendCommand = new FriendCommand(this, friendService, friendMenuService);
        var friendCmd = this.getCommand("amigo");
        if (friendCmd != null) {
            friendCmd.setExecutor(friendCommand);
            friendCmd.setTabCompleter(friendCommand);
        } else {
            this.getLogger().severe("El comando 'amigo' no está registrado en plugin.yml");
        }

        if (this.getConfig().getBoolean("rank-editor.enable", true)) {
            int port = this.getConfig().getInt("rank-editor.port", 25580);
            String bind = this.getConfig().getString("rank-editor.bind-address", "0.0.0.0");
            boolean https = this.getConfig().getBoolean("rank-editor.use-https", true);
            this.webEditorServer = new WebEditorServer(this);
            this.webEditorServer.start(port, bind, https);
        }

        this.luckPermsRankChangeListener = new LuckPermsRankChangeListener(this);
        this.luckPermsRankChangeListener.register();

        if (this.getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new EffectosExpansion(this).register();
            this.getLogger().info("PlaceholderAPI conectado correctamente.");
        } else {
            this.getLogger().warning("PlaceholderAPI no encontrado. Los placeholders no funcionarán.");
        }

        this.getLogger().info("CustomEffects habilitado con éxito.");
    }

    @Override
    public void onDisable() {
        if (this.luckPermsRankChangeListener != null) {
            this.luckPermsRankChangeListener.unregister();
        }
        if (this.animatedPrefixService != null) {
            this.animatedPrefixService.stop();
        }
        if (this.webEditorServer != null) {
            this.webEditorServer.stop();
        }
        if (this.database != null) {
            this.database.close();
        }
        this.getLogger().info("CustomEffects deshabilitado y DB desconectada.");
    }

    public void loadEffects() {
        this.dataManager.loadAll();
    }

    public DatabaseManager getDatabase() {
        return this.database;
    }

    public DataManager getDataManager() {
        return this.dataManager;
    }

    public MenuService getMenuService() {
        return this.menuService;
    }

    public VoucherService getVoucherService() {
        return this.voucherService;
    }

    public LuckPermsService getLuckPermsService() {
        return this.luckPermsService;
    }

    public FriendService getFriendService() {
        return this.friendService;
    }

    public FriendMenuService getFriendMenuService() {
        return this.friendMenuService;
    }

    public FriendListener getFriendListener() {
        return this.friendListener;
    }

    public AnimatedPrefixService getAnimatedPrefixService() {
        return this.animatedPrefixService;
    }

    public String getMainMenuTitle() {
        return this.dataManager.getMainMenuTitle();
    }

    public int getMainMenuSize() {
        return this.dataManager.getMainMenuSize();
    }

    public int getSubMenuSize() {
        return this.dataManager.getSubMenuSize();
    }

    public int getEffectsPerPage() {
        return this.dataManager.getEffectsPerPage();
    }

    public List<String> getDefaultLore() {
        return this.getConfig().getStringList("default-lore");
    }

    public WebEditorServer getWebEditorServer() {
        return this.webEditorServer;
    }

    public String getLuckPermsPrefix(Player player) {
        return this.luckPermsService.getPrefix(player);
    }

    public String getPlayerGroup(Player player) {
        return this.luckPermsService.getPlayerGroup(player);
    }

    public boolean canPlayerSeePrefix(Player player, String prefixGroup) {
        List<String> hierarchy = this.getConfig().getStringList("prefix-system.rank-hierarchy");
        return this.luckPermsService.canPlayerSeePrefix(player, prefixGroup, hierarchy);
    }
}
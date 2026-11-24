package fr.vortexsmp.vortexessentials.shop;

import fr.vortexsmp.vortexessentials.VortexEssentials;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Pont vers l'économie Vault / VaultUnlocked, par réflexion : aucune dépendance de compilation.
 * VaultUnlocked conserve l'API Vault (net.milkbowl.vault.economy.Economy) : il suffit d'avoir
 * VaultUnlocked (ou Vault) et un plugin d'économie qui enregistre un fournisseur.
 */
public final class Money {
    private final VortexEssentials plugin;
    private Object eco;
    private Method getBalance, has, withdraw, deposit, format;

    public Money(VortexEssentials plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void resolve() {
        if (eco != null) return;
        try {
            Class<?> c = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration((Class) c);
            if (rsp == null) return;
            Object provider = rsp.getProvider();
            getBalance = c.getMethod("getBalance", OfflinePlayer.class);
            has = c.getMethod("has", OfflinePlayer.class, double.class);
            withdraw = c.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            deposit = c.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            format = c.getMethod("format", double.class);
            eco = provider;
            plugin.getLogger().info("Économie : " + provider.getClass().getName());
        } catch (Throwable t) {
            eco = null;
        }
    }

    public boolean available() {
        resolve();
        return eco != null;
    }

    public double balance(OfflinePlayer p) {
        if (!available()) return 0;
        try { return ((Number) getBalance.invoke(eco, p)).doubleValue(); } catch (Throwable t) { return 0; }
    }

    public boolean has(OfflinePlayer p, double amount) {
        if (!available()) return false;
        try { return (boolean) has.invoke(eco, p, amount); } catch (Throwable t) { return false; }
    }

    private boolean ok(Object response) throws Exception {
        return (boolean) response.getClass().getMethod("transactionSuccess").invoke(response);
    }

    public boolean withdraw(OfflinePlayer p, double amount) {
        if (!available() || amount < 0) return false;
        try { return ok(withdraw.invoke(eco, p, amount)); } catch (Throwable t) { return false; }
    }

    public boolean deposit(OfflinePlayer p, double amount) {
        if (!available() || amount < 0) return false;
        try { return ok(deposit.invoke(eco, p, amount)); } catch (Throwable t) { return false; }
    }

    public String format(double amount) {
        if (available()) {
            try { return String.valueOf(format.invoke(eco, amount)); } catch (Throwable ignored) { }
        }
        return String.format(Locale.US, "%.2f", amount);
    }
}

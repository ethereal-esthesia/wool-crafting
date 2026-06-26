// SPDX-License-Identifier: GPL-3.0-only

package dev.shane.minecraft.woolcrafting;

import com.google.common.collect.ImmutableMultimap;
import net.kyori.adventure.text.Component;
import org.bukkit.DyeColor;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class WoolCraftingPlugin extends JavaPlugin implements Listener {

    private final List<NamespacedKey> recipeKeys = new ArrayList<>();
    private final List<NamespacedKey> woolWearRecipeKeys = new ArrayList<>();
    private NamespacedKey woolWearPieceKey;
    private NamespacedKey woolWearColorKey;
    private NamespacedKey wovenSaddleKey;
    private NamespacedKey wovenSaddleRecipeKey;
    private NamespacedKey wovenSacKey;
    private NamespacedKey wovenSacRecipeKey;

    @Override
    public void onEnable() {
        woolWearPieceKey = new NamespacedKey(this, "wool_wear_piece");
        woolWearColorKey = new NamespacedKey(this, "wool_wear_color");
        wovenSaddleKey = new NamespacedKey(this, "woven_saddle");
        wovenSacKey = new NamespacedKey(this, "woven_sac");

        registerRecipes();
        getServer().getPluginManager().registerEvents(this, this);
        discoverRecipesForOnlinePlayers();
        updateLoadedTradeVillagers();
        getLogger().info("Enabled. Registered wool crafting recipes and recipe book unlocks.");
    }

    @Override
    public void onDisable() {
        removeRecipes();
        getServer().updateRecipes();
        getLogger().info("Disabled.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        getServer().getScheduler().runTask(this, () -> discoverRecipesForPlayer(event.getPlayer()));
    }

    @EventHandler
    public void onEntityPickupItem(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (shouldDiscoverWovenSaddle(event.getItem().getItemStack())) {
            discoverWovenSaddleRecipe(player);
        }
        if (shouldDiscoverWovenSac(event.getItem().getItemStack())) {
            discoverWovenSacRecipe(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (shouldDiscoverWovenSaddle(event.getCurrentItem()) || shouldDiscoverWovenSaddle(event.getCursor())) {
            getServer().getScheduler().runTask(this, () -> discoverWovenSaddleRecipe(player));
        }
        if (shouldDiscoverWovenSac(event.getCurrentItem()) || shouldDiscoverWovenSac(event.getCursor())) {
            getServer().getScheduler().runTask(this, () -> discoverWovenSacRecipe(player));
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (shouldDiscoverWovenSaddle(event.getOldCursor())) {
            getServer().getScheduler().runTask(this, () -> discoverWovenSaddleRecipe(player));
        }
        if (shouldDiscoverWovenSac(event.getOldCursor())) {
            getServer().getScheduler().runTask(this, () -> discoverWovenSacRecipe(player));
        }
    }

    @EventHandler
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof Villager villager) {
            getServer().getScheduler().runTask(this, () -> updateTradeVillager(villager));
        }
    }

    @EventHandler
    public void onVillagerCareerChange(VillagerCareerChangeEvent event) {
        if (isManagedTradeProfession(event.getProfession())) {
            getServer().getScheduler().runTask(this, () -> updateTradeVillager(event.getEntity()));
        }
    }

    @EventHandler
    public void onVillagerAcquireTrade(VillagerAcquireTradeEvent event) {
        if (!(event.getEntity() instanceof Villager villager) || !isManagedTradeVillager(villager)) {
            return;
        }

        MerchantRecipe replacement = rewriteVillagerRecipe(villager, event.getRecipe());
        if (replacement != null) {
            event.setRecipe(replacement);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Villager villager) {
                updateTradeVillager(villager);
            }
        }
    }

    private void registerRecipes() {
        removeRecipes();

        for (WoolColor color : WoolColor.values()) {
            for (WoolWearPiece piece : WoolWearPiece.values()) {
                NamespacedKey key = new NamespacedKey(this, color.keyPrefix() + "_" + piece.keySuffix());
                ShapedRecipe recipe = new ShapedRecipe(key, createWoolWear(piece, color));
                recipe.shape(piece.shape());
                recipe.setGroup(piece.recipeGroup());
                recipe.setCategory(CraftingBookCategory.EQUIPMENT);
                recipe.setIngredient('W', color.woolMaterial());
                getServer().addRecipe(recipe);
                recipeKeys.add(key);
                woolWearRecipeKeys.add(key);
            }
        }

        registerWovenSaddleRecipe();
        registerWovenSacRecipe();
        getServer().updateRecipes();
    }

    private void removeRecipes() {
        for (NamespacedKey key : recipeKeys) {
            getServer().removeRecipe(key);
        }
        recipeKeys.clear();
        woolWearRecipeKeys.clear();
        wovenSaddleRecipeKey = null;
        wovenSacRecipeKey = null;
    }

    private void discoverRecipesForOnlinePlayers() {
        for (Player player : getServer().getOnlinePlayers()) {
            discoverRecipesForPlayer(player);
        }
    }

    private void discoverRecipesForPlayer(Player player) {
        discoverWoolWearRecipes(player);
        if (hasWovenSaddleRecipeTrigger(player)) {
            discoverWovenSaddleRecipe(player);
        }
        if (hasWovenSacRecipeTrigger(player)) {
            discoverWovenSacRecipe(player);
        }
    }

    private void discoverWoolWearRecipes(Player player) {
        player.discoverRecipes(woolWearRecipeKeys);
    }

    private void discoverWovenSaddleRecipe(Player player) {
        if (wovenSaddleRecipeKey != null) {
            player.discoverRecipe(wovenSaddleRecipeKey);
        }
    }

    private void discoverWovenSacRecipe(Player player) {
        if (wovenSacRecipeKey != null) {
            player.discoverRecipe(wovenSacRecipeKey);
        }
    }

    @SuppressWarnings("deprecation")
    private ItemStack createWoolWear(WoolWearPiece piece, WoolColor color) {
        return createWoolWear(piece, color, piece.displayName());
    }

    @SuppressWarnings("deprecation")
    private ItemStack createWoolWear(WoolWearPiece piece, WoolColor color, String displayName) {
        ItemStack item = new ItemStack(piece.material());
        ItemMeta baseMeta = item.getItemMeta();
        if (!(baseMeta instanceof LeatherArmorMeta meta)) {
            throw new IllegalStateException(piece.material() + " did not provide leather armor metadata");
        }

        meta.setColor(color.dyeColor().getColor());
        meta.itemName(Component.text(displayName));
        meta.setUnbreakable(true);
        meta.setAttributeModifiers(ImmutableMultimap.<Attribute, AttributeModifier>of());
        meta.addItemFlags(
            ItemFlag.HIDE_ATTRIBUTES,
            ItemFlag.HIDE_UNBREAKABLE,
            ItemFlag.HIDE_DYE,
            ItemFlag.HIDE_ADDITIONAL_TOOLTIP
        );
        meta.getPersistentDataContainer().set(woolWearPieceKey, PersistentDataType.STRING, piece.keySuffix());
        meta.getPersistentDataContainer().set(woolWearColorKey, PersistentDataType.STRING, color.keyPrefix());

        item.setItemMeta(meta);
        return item;
    }

    private void updateLoadedTradeVillagers() {
        for (World world : getServer().getWorlds()) {
            for (Villager villager : world.getEntitiesByClass(Villager.class)) {
                updateTradeVillager(villager);
            }
        }
    }

    private void updateTradeVillager(Villager villager) {
        if (!isManagedTradeVillager(villager)) {
            return;
        }

        villager.customName(Component.text("Tailor"));
        villager.setCustomNameVisible(true);

        List<MerchantRecipe> recipes = villager.getRecipes();
        boolean changed = false;
        for (int index = 0; index < recipes.size(); index++) {
            MerchantRecipe replacement = rewriteVillagerRecipe(villager, recipes.get(index));
            if (replacement != null) {
                recipes.set(index, replacement);
                changed = true;
            }
        }

        if (changed) {
            villager.setRecipes(recipes);
        }
    }

    private boolean isManagedTradeVillager(Villager villager) {
        return isManagedTradeProfession(villager.getProfession());
    }

    private boolean isManagedTradeProfession(Villager.Profession profession) {
        return profession == Villager.Profession.LEATHERWORKER || profession == Villager.Profession.SHEPHERD;
    }

    private MerchantRecipe rewriteVillagerRecipe(Villager villager, MerchantRecipe recipe) {
        if (villager.getProfession() == Villager.Profession.SHEPHERD) {
            return rewriteShepherdRecipe(recipe);
        }
        if (villager.getProfession() == Villager.Profession.LEATHERWORKER) {
            return rewriteLeatherworkerRecipe(recipe);
        }
        return null;
    }

    private MerchantRecipe rewriteLeatherworkerRecipe(MerchantRecipe recipe) {
        if (isRabbitHidePurchase(recipe)) {
            return createWovenSacTrade(recipe);
        }
        if (isSaddleSale(recipe)) {
            return copyRecipeWithResult(recipe, createWovenSaddle());
        }

        WoolWearPiece tradedPiece = tradedLeatherArmorPiece(recipe);
        if (tradedPiece != null) {
            return copyRecipeWithResult(recipe, createWoolWear(tradedPiece, WoolColor.WHITE));
        }

        if (hasLeatherIngredient(recipe)) {
            MerchantRecipe replacement = copyRecipeWithResult(recipe, recipe.getResult());
            List<ItemStack> ingredients = new ArrayList<>();
            for (ItemStack ingredient : recipe.getIngredients()) {
                if (ingredient.getType() == Material.LEATHER) {
                    ingredients.add(new ItemStack(Material.STRING, ingredient.getAmount()));
                } else {
                    ingredients.add(ingredient.clone());
                }
            }
            replacement.setIngredients(ingredients);
            return replacement;
        }

        return null;
    }

    private MerchantRecipe rewriteShepherdRecipe(MerchantRecipe recipe) {
        if (hasStringTag(recipe.getResult(), woolWearPieceKey)) {
            return null;
        }
        if (isColoredWoolSale(recipe)) {
            return createClothGearTrade(recipe, WoolWearPiece.BOOTS, "Cloth Boots");
        }
        if (isColoredCarpetSale(recipe)) {
            return createClothGearTrade(recipe, WoolWearPiece.CAP, "Cloth Cap");
        }
        if (isColoredBedSale(recipe)) {
            return createClothGearTrade(recipe, WoolWearPiece.JACKET, "Cloth Vest");
        }
        if (isMapMarkerSale(recipe)) {
            return createClothGearTrade(recipe, WoolWearPiece.TROUSERS, "Cloth Leggings");
        }
        return null;
    }

    private MerchantRecipe copyRecipeWithResult(MerchantRecipe recipe, ItemStack result) {
        MerchantRecipe replacement = new MerchantRecipe(
            result,
            recipe.getUses(),
            recipe.getMaxUses(),
            recipe.hasExperienceReward(),
            recipe.getVillagerExperience(),
            recipe.getPriceMultiplier(),
            recipe.getDemand(),
            recipe.getSpecialPrice(),
            recipe.shouldIgnoreDiscounts()
        );
        replacement.setIngredients(cloneIngredients(recipe));
        return replacement;
    }

    private List<ItemStack> cloneIngredients(MerchantRecipe recipe) {
        List<ItemStack> ingredients = new ArrayList<>();
        for (ItemStack ingredient : recipe.getIngredients()) {
            ingredients.add(ingredient.clone());
        }
        return ingredients;
    }

    private boolean isRabbitHidePurchase(MerchantRecipe recipe) {
        return recipe.getResult().getType() == Material.EMERALD && hasIngredient(recipe, Material.RABBIT_HIDE);
    }

    private MerchantRecipe createWovenSacTrade(MerchantRecipe original) {
        MerchantRecipe replacement = copyRecipeWithResult(original, createWovenSac());
        replacement.setIngredients(List.of(new ItemStack(Material.EMERALD, randomWovenSacPrice())));
        return replacement;
    }

    private int randomWovenSacPrice() {
        return ThreadLocalRandom.current().nextInt(16, 25);
    }

    private MerchantRecipe createClothGearTrade(MerchantRecipe original, WoolWearPiece piece, String displayName) {
        MerchantRecipe replacement = copyRecipeWithResult(original, createWoolWear(piece, WoolColor.WHITE, displayName));
        replacement.setIngredients(List.of(new ItemStack(Material.EMERALD, randomClothGearPrice())));
        return replacement;
    }

    private int randomClothGearPrice() {
        return ThreadLocalRandom.current().nextInt(12, 25);
    }

    private boolean isColoredWoolSale(MerchantRecipe recipe) {
        return isSale(recipe) && isWool(recipe.getResult().getType());
    }

    private boolean isColoredCarpetSale(MerchantRecipe recipe) {
        return isSale(recipe) && recipe.getResult().getType().name().endsWith("_CARPET");
    }

    private boolean isColoredBedSale(MerchantRecipe recipe) {
        return isSale(recipe) && recipe.getResult().getType().name().endsWith("_BED");
    }

    private boolean isMapMarkerSale(MerchantRecipe recipe) {
        return isSale(recipe) && recipe.getResult().getType().name().endsWith("_BANNER");
    }

    private boolean isSale(MerchantRecipe recipe) {
        return recipe.getResult().getType() != Material.EMERALD;
    }

    private boolean isSaddleSale(MerchantRecipe recipe) {
        ItemStack result = recipe.getResult();
        return result.getType() == Material.SADDLE && !hasBooleanTag(result, wovenSaddleKey);
    }

    private WoolWearPiece tradedLeatherArmorPiece(MerchantRecipe recipe) {
        if (hasStringTag(recipe.getResult(), woolWearPieceKey)) {
            return null;
        }

        Material result = recipe.getResult().getType();
        for (WoolWearPiece piece : WoolWearPiece.values()) {
            if (piece.material() == result) {
                return piece;
            }
        }
        return null;
    }

    private boolean hasLeatherIngredient(MerchantRecipe recipe) {
        return hasIngredient(recipe, Material.LEATHER);
    }

    private boolean hasIngredient(MerchantRecipe recipe, Material material) {
        for (ItemStack ingredient : recipe.getIngredients()) {
            if (ingredient.getType() == material) {
                return true;
            }
        }
        return false;
    }

    private boolean hasBooleanTag(ItemStack item, NamespacedKey key) {
        if (!item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.BOOLEAN);
    }

    private boolean hasStringTag(ItemStack item, NamespacedKey key) {
        if (!item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(key, PersistentDataType.STRING);
    }

    private void registerWovenSacRecipe() {
        NamespacedKey key = new NamespacedKey(this, "woven_sac");
        wovenSacRecipeKey = key;
        ShapedRecipe recipe = new ShapedRecipe(key, createWovenSac());
        recipe.shape(
            "SWS",
            "W W",
            "WWW"
        );
        recipe.setGroup("woven_sac");
        recipe.setCategory(CraftingBookCategory.EQUIPMENT);
        recipe.setIngredient('S', Material.STRING);
        recipe.setIngredient('W', new RecipeChoice.MaterialChoice(woolMaterials()));
        getServer().addRecipe(recipe);
        recipeKeys.add(key);
    }

    private ItemStack createWovenSac() {
        ItemStack item = new ItemStack(Material.BUNDLE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("BUNDLE did not provide item metadata");
        }

        meta.itemName(Component.text("Woven Sac"));
        meta.getPersistentDataContainer().set(wovenSacKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    private void registerWovenSaddleRecipe() {
        NamespacedKey key = new NamespacedKey(this, "woven_saddle");
        wovenSaddleRecipeKey = key;
        ShapedRecipe recipe = new ShapedRecipe(key, createWovenSaddle());
        recipe.shape(
            "HHH",
            "WSW"
        );
        recipe.setGroup("woven_saddle");
        recipe.setCategory(CraftingBookCategory.EQUIPMENT);
        recipe.setIngredient('H', Material.HONEYCOMB);
        recipe.setIngredient('W', new RecipeChoice.MaterialChoice(woolMaterials()));
        recipe.setIngredient('S', new RecipeChoice.MaterialChoice(slabMaterials()));
        getServer().addRecipe(recipe);
        recipeKeys.add(key);
    }

    private ItemStack createWovenSaddle() {
        ItemStack item = new ItemStack(Material.SADDLE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("SADDLE did not provide item metadata");
        }

        meta.itemName(Component.text("Woven Saddle"));
        meta.getPersistentDataContainer().set(wovenSaddleKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    private boolean hasWovenSaddleRecipeTrigger(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (shouldDiscoverWovenSaddle(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasWovenSacRecipeTrigger(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (shouldDiscoverWovenSac(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldDiscoverWovenSaddle(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (item.getType() == Material.HONEYCOMB) {
            return true;
        }
        if (item.getType() != Material.SADDLE || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(wovenSaddleKey, PersistentDataType.BOOLEAN);
    }

    private boolean shouldDiscoverWovenSac(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        if (isWool(item.getType())) {
            return true;
        }
        if (item.getType() != Material.BUNDLE || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(wovenSacKey, PersistentDataType.BOOLEAN);
    }

    private boolean isWool(Material material) {
        for (WoolColor color : WoolColor.values()) {
            if (color.woolMaterial() == material) {
                return true;
            }
        }
        return false;
    }

    private List<Material> woolMaterials() {
        List<Material> materials = new ArrayList<>();
        for (WoolColor color : WoolColor.values()) {
            materials.add(color.woolMaterial());
        }
        return materials;
    }

    private List<Material> slabMaterials() {
        List<Material> materials = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.isItem() && material.name().endsWith("_SLAB")) {
                materials.add(material);
            }
        }
        return materials;
    }

    private enum WoolWearPiece {
        CAP("cap", "Wool Cap", Material.LEATHER_HELMET, new String[] {
            "WWW",
            "W W"
        }),
        JACKET("jacket", "Wool Jacket", Material.LEATHER_CHESTPLATE, new String[] {
            "W W",
            "WWW",
            "WWW"
        }),
        TROUSERS("trousers", "Wool Trousers", Material.LEATHER_LEGGINGS, new String[] {
            "WWW",
            "W W",
            "W W"
        }),
        BOOTS("boots", "Wool Boots", Material.LEATHER_BOOTS, new String[] {
            "W W",
            "W W"
        });

        private final String keySuffix;
        private final String displayName;
        private final Material material;
        private final String[] shape;

        WoolWearPiece(String keySuffix, String displayName, Material material, String[] shape) {
            this.keySuffix = keySuffix;
            this.displayName = displayName;
            this.material = material;
            this.shape = shape;
        }

        private String keySuffix() {
            return keySuffix;
        }

        private String recipeGroup() {
            return "wool_wear_" + keySuffix;
        }

        private String displayName() {
            return displayName;
        }

        private Material material() {
            return material;
        }

        private String[] shape() {
            return shape.clone();
        }
    }

    private enum WoolColor {
        WHITE("white", Material.WHITE_WOOL, DyeColor.WHITE),
        ORANGE("orange", Material.ORANGE_WOOL, DyeColor.ORANGE),
        MAGENTA("magenta", Material.MAGENTA_WOOL, DyeColor.MAGENTA),
        LIGHT_BLUE("light_blue", Material.LIGHT_BLUE_WOOL, DyeColor.LIGHT_BLUE),
        YELLOW("yellow", Material.YELLOW_WOOL, DyeColor.YELLOW),
        LIME("lime", Material.LIME_WOOL, DyeColor.LIME),
        PINK("pink", Material.PINK_WOOL, DyeColor.PINK),
        GRAY("gray", Material.GRAY_WOOL, DyeColor.GRAY),
        LIGHT_GRAY("light_gray", Material.LIGHT_GRAY_WOOL, DyeColor.LIGHT_GRAY),
        CYAN("cyan", Material.CYAN_WOOL, DyeColor.CYAN),
        PURPLE("purple", Material.PURPLE_WOOL, DyeColor.PURPLE),
        BLUE("blue", Material.BLUE_WOOL, DyeColor.BLUE),
        BROWN("brown", Material.BROWN_WOOL, DyeColor.BROWN),
        GREEN("green", Material.GREEN_WOOL, DyeColor.GREEN),
        RED("red", Material.RED_WOOL, DyeColor.RED),
        BLACK("black", Material.BLACK_WOOL, DyeColor.BLACK);

        private final DyeColor dyeColor;
        private final Material woolMaterial;
        private final String keyPrefix;

        WoolColor(String keyPrefix, Material woolMaterial, DyeColor dyeColor) {
            this.dyeColor = dyeColor;
            this.keyPrefix = keyPrefix;
            this.woolMaterial = woolMaterial;
        }

        private DyeColor dyeColor() {
            return dyeColor;
        }

        private Material woolMaterial() {
            return woolMaterial;
        }

        private String keyPrefix() {
            return keyPrefix;
        }
    }
}

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
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

public final class WoolCraftingPlugin extends JavaPlugin implements Listener {

    private final List<NamespacedKey> recipeKeys = new ArrayList<>();
    private NamespacedKey woolWearPieceKey;
    private NamespacedKey woolWearColorKey;
    private NamespacedKey wovenSaddleKey;
    private NamespacedKey wovenSacKey;

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
            }
        }

        for (WoolColor color : WoolColor.values()) {
            for (Material slab : slabMaterials()) {
                registerWovenSaddleRecipe(color, slab);
            }
            registerWovenBundleRecipe(color);
        }
        getServer().updateRecipes();
    }

    private void removeRecipes() {
        for (NamespacedKey key : recipeKeys) {
            getServer().removeRecipe(key);
        }
        recipeKeys.clear();
    }

    private void discoverRecipesForOnlinePlayers() {
        for (Player player : getServer().getOnlinePlayers()) {
            discoverRecipesForPlayer(player);
        }
    }

    private void discoverRecipesForPlayer(Player player) {
        player.discoverRecipes(recipeKeys);
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

        rewriteRecipes(villager, recipe -> rewriteVillagerRecipe(villager, recipe));
    }

    static boolean rewriteRecipes(
        Villager villager,
        Function<MerchantRecipe, MerchantRecipe> rewriter
    ) {
        List<MerchantRecipe> recipes = new ArrayList<>(villager.getRecipes());
        boolean changed = false;
        for (int index = 0; index < recipes.size(); index++) {
            MerchantRecipe replacement = rewriter.apply(recipes.get(index));
            if (replacement != null) {
                recipes.set(index, replacement);
                changed = true;
            }
        }

        if (changed) {
            villager.setRecipes(recipes);
        }
        return changed;
    }

    private boolean isManagedTradeVillager(Villager villager) {
        return isManagedTradeProfession(villager.getProfession());
    }

    private boolean isManagedTradeProfession(Villager.Profession profession) {
        return profession == Villager.Profession.SHEPHERD;
    }

    private MerchantRecipe rewriteVillagerRecipe(Villager villager, MerchantRecipe recipe) {
        if (villager.getProfession() == Villager.Profession.SHEPHERD) {
            return rewriteShepherdRecipe(recipe);
        }
        return null;
    }

    private MerchantRecipe rewriteShepherdRecipe(MerchantRecipe recipe) {
        if (hasStringTag(recipe.getResult(), woolWearPieceKey)) {
            return null;
        }
        if (hasBooleanTag(recipe.getResult(), wovenSaddleKey)) {
            return null;
        }
        if (isColoredWoolSale(recipe)) {
            return createClothGearTrade(recipe, WoolWearPiece.BOOTS, "Cloth Boots");
        }
        if (isColoredCarpetSale(recipe)) {
            return createClothGearTrade(recipe, WoolWearPiece.CAP, "Cloth Cap");
        }
        if (isColoredBedSale(recipe)) {
            return createClothGearTrade(recipe, WoolWearPiece.TUNIC, "Cloth Tunic");
        }
        if (isMapMarkerSale(recipe)) {
            return createClothGearTrade(recipe, WoolWearPiece.PANTS, "Cloth Pants");
        }
        if (isPaintingSale(recipe)) {
            return createClothSaddleTrade(recipe);
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

    private MerchantRecipe createClothGearTrade(MerchantRecipe original, WoolWearPiece piece, String displayName) {
        MerchantRecipe replacement = copyRecipeWithResult(original, createWoolWear(piece, WoolColor.WHITE, displayName));
        replacement.setIngredients(List.of(new ItemStack(Material.EMERALD, randomClothGearPrice())));
        return replacement;
    }

    private MerchantRecipe createClothSaddleTrade(MerchantRecipe original) {
        MerchantRecipe replacement = copyRecipeWithResult(original, createWovenSaddle("Cloth Saddle"));
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

    private boolean isPaintingSale(MerchantRecipe recipe) {
        return isSale(recipe) && recipe.getResult().getType() == Material.PAINTING;
    }

    private boolean isSale(MerchantRecipe recipe) {
        return recipe.getResult().getType() != Material.EMERALD;
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

    private void registerWovenBundleRecipe(WoolColor color) {
        NamespacedKey key = new NamespacedKey(this, color.keyPrefix() + "_woven_bundle");
        ShapedRecipe recipe = new ShapedRecipe(key, createWovenSac());
        recipe.shape(
            "SWS",
            "W W",
            "WWW"
        );
        recipe.setGroup("woven_sac");
        recipe.setCategory(CraftingBookCategory.EQUIPMENT);
        recipe.setIngredient('S', Material.STRING);
        recipe.setIngredient('W', color.woolMaterial());
        getServer().addRecipe(recipe);
        recipeKeys.add(key);
    }

    private ItemStack createWovenSac() {
        ItemStack item = new ItemStack(Material.BUNDLE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("BUNDLE did not provide item metadata");
        }

        meta.itemName(Component.text("Woven Bundle"));
        meta.getPersistentDataContainer().set(wovenSacKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    private void registerWovenSaddleRecipe(WoolColor color, Material slab) {
        NamespacedKey key = new NamespacedKey(this, color.keyPrefix() + "_" + recipeKeyPart(slab) + "_woven_saddle");
        ShapedRecipe recipe = new ShapedRecipe(key, createWovenSaddle());
        recipe.shape(
            "HHH",
            "WSW"
        );
        recipe.setGroup("woven_saddle");
        recipe.setCategory(CraftingBookCategory.EQUIPMENT);
        recipe.setIngredient('H', Material.HONEYCOMB);
        recipe.setIngredient('W', color.woolMaterial());
        recipe.setIngredient('S', slab);
        getServer().addRecipe(recipe);
        recipeKeys.add(key);
    }

    private ItemStack createWovenSaddle() {
        return createWovenSaddle("Woven Saddle");
    }

    private ItemStack createWovenSaddle(String displayName) {
        ItemStack item = new ItemStack(Material.SADDLE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            throw new IllegalStateException("SADDLE did not provide item metadata");
        }

        meta.itemName(Component.text(displayName));
        meta.getPersistentDataContainer().set(wovenSaddleKey, PersistentDataType.BOOLEAN, true);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isWool(Material material) {
        for (WoolColor color : WoolColor.values()) {
            if (color.woolMaterial() == material) {
                return true;
            }
        }
        return false;
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

    private String recipeKeyPart(Material material) {
        return material.name().toLowerCase();
    }

    private enum WoolWearPiece {
        CAP("cap", "Wool Cap", Material.LEATHER_HELMET, new String[] {
            "WWW",
            "W W"
        }),
        TUNIC("tunic", "Wool Tunic", Material.LEATHER_CHESTPLATE, new String[] {
            "W W",
            "WWW",
            "WWW"
        }),
        PANTS("pants", "Wool Pants", Material.LEATHER_LEGGINGS, new String[] {
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

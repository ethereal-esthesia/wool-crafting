package dev.shane.minecraft.woolcrafting;

import org.bukkit.entity.Villager;
import org.bukkit.inventory.MerchantRecipe;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WoolCraftingPluginTest {

    @Test
    void rewritesRecipeFromImmutableVillagerList() {
        Villager villager = mock(Villager.class);
        MerchantRecipe original = mock(MerchantRecipe.class);
        MerchantRecipe untouched = mock(MerchantRecipe.class);
        MerchantRecipe replacement = mock(MerchantRecipe.class);
        when(villager.getRecipes()).thenReturn(List.of(original, untouched));

        boolean changed = assertDoesNotThrow(() -> WoolCraftingPlugin.rewriteRecipes(
            villager,
            recipe -> recipe == original ? replacement : null
        ));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MerchantRecipe>> recipesCaptor = ArgumentCaptor.forClass(List.class);
        verify(villager).setRecipes(recipesCaptor.capture());
        List<MerchantRecipe> rewritten = recipesCaptor.getValue();

        assertAll(
            () -> assertTrue(changed),
            () -> assertSame(replacement, rewritten.get(0)),
            () -> assertSame(untouched, rewritten.get(1)),
            () -> assertDoesNotThrow(() -> rewritten.set(0, original)),
            () -> assertSame(original, villager.getRecipes().get(0), "source list must remain unchanged")
        );
    }

    @Test
    void leavesVillagerRecipesUntouchedWhenNothingMatches() {
        Villager villager = mock(Villager.class);
        MerchantRecipe original = mock(MerchantRecipe.class);
        when(villager.getRecipes()).thenReturn(List.of(original));

        boolean changed = WoolCraftingPlugin.rewriteRecipes(villager, recipe -> null);

        assertFalse(changed);
        verify(villager, never()).setRecipes(org.mockito.ArgumentMatchers.anyList());
    }
}

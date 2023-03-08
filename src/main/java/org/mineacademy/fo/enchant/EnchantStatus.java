package org.mineacademy.fo.enchant;

import lombok.Getter;
import org.bukkit.enchantments.Enchantment;
import org.mineacademy.fo.Logger;

/**
 * An easy way to represent checks results on enchanting.
 *
 * @since 6.2.5.4
 * @author Rubix327
 */
public class EnchantStatus {

    /**
     * The enchantment has passed all checks and can be applied to the item.
     */
    public static EnchantStatus ALLOWED = new EnchantStatus(0, true);
    /**
     * The enchantment is conflicting with some other enchantments on the item.
     * @see SimpleEnchantment#conflictsWith(Enchantment)
     */
    public static EnchantStatus CONFLICT = new EnchantStatus(1, true);
    /**
     * The enchantment is not suitable for this type of item.
     * @see SimpleEnchantment#getCustomItemTarget()
     * @see SimpleEnchantmentTarget
     */
    public static EnchantStatus NOT_IN_ITEM_TARGET = new EnchantStatus(2, true);
    /**
     * The enchantment is not suitable for this item material.
     * @see SimpleEnchantment#enchantMaterial()
     */
    public static EnchantStatus NOT_IN_MATERIAL = new EnchantStatus(3, true);
    /**
     * The enchantment is not suitable for this item material.
     * @see SimpleEnchantment#enchantMaterials()
     */
    public static EnchantStatus NOT_IN_MATERIALS = new EnchantStatus(4, true);
    /**
     * The given level of the enchantment is higher than the maximum level.
     * @see SimpleEnchantment#getMaxLevel()
     */
    public static EnchantStatus LEVEL_TOO_HIGH = new EnchantStatus(5, true);
    /**
     * The item already has the same enchantment with the same level.
     */
    public static EnchantStatus ALREADY_ENCHANTED = new EnchantStatus(6, true);

    /**
     * The last code used.<br>
     * Begins with 10 because 0-9 codes are reserved for the built-in statuses.
     */
    public static int lastCode = 10;

    @Getter
    private final int code;
    @Getter
    private final boolean builtin;

    /**
     * The constructor with the automatic code generation.
     */
    public EnchantStatus() {
        this(lastCode++);
    }

    /**
     * @param code the unique code of the status. Must be greater than 9. Must not be repeated.
     */
    public EnchantStatus(int code) {
        if (code >= 0 && code <= 9){
            Logger.printErrors("EnchantStatus codes from 0 to 9 is reserved for the system.",
                    "Please choose a code greater than 9 or use no-args constructor.");
            throw new RuntimeException("EnchantStatus codes from 0 to 9 is reserved for the system.");
        }
        if (code <= lastCode){
            Logger.printErrors("EnchantStatus code " + code + " is already taken by another status.",
                    "Please choose another code for your EnchantStatus.");
            throw new RuntimeException("EnchantStatus code " + code + " is already taken by another status.");
        }
        this.code = code;
        this.builtin = false;
    }

    private EnchantStatus(int code, boolean builtin){
        this.code = code;
        this.builtin = builtin;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EnchantStatus that = (EnchantStatus) o;

        return code == that.code;
    }

    @Override
    public int hashCode() {
        return code;
    }
}

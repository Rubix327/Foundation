package org.mineacademy.fo.menu;

import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Represents locked slots of menus.
 */
public enum LockedSlotsFigure {

    BOUNDS_9X3(true, 10, 11, 12, 13, 14, 15, 16),
    BOUNDS_9X4(true, 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25),
    BOUNDS_9X5(true, 10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34),

    BOUNDS_9X6(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53),
    CIRCLE_9X6(true, 12, 13, 14, 20, 21, 22, 23, 24, 29, 30, 31, 32, 33, 39, 40, 41),
    ROWS_9X6(0, 1, 2, 3, 4, 5, 6, 7, 8, 45, 46, 47, 48, 49, 50, 51, 52, 53),
    COLUMNS_9X6(0, 8, 9, 18, 27, 36, 45, 17, 26, 35, 44, 53),
    SIX_SLOTS_9X6(true, 21, 22, 23, 30, 31, 32),
    TWO_SLOTS_9X6(true, 22, 31),
    ONE_SLOT_9X3(true, 13),
    ONE_SLOT_9X1(true, 4),
    NONE(-1);

    public enum Raw{
        BOUNDS(NONE, NONE, BOUNDS_9X3, BOUNDS_9X4, BOUNDS_9X5, BOUNDS_9X6),
        CIRCLE(NONE, NONE, NONE, NONE, NONE, CIRCLE_9X6),
        COLUMNS(NONE, NONE, NONE, NONE, NONE, COLUMNS_9X6),
        SIX_SLOTS(NONE, NONE, NONE, NONE, NONE, SIX_SLOTS_9X6),
        TWO_SLOTS(NONE, NONE, NONE, NONE, NONE, TWO_SLOTS_9X6),
        ONE_SLOT(ONE_SLOT_9X1, NONE, ONE_SLOT_9X3, NONE, NONE, NONE);

        private final LockedSlotsFigure[] mod;

        Raw(LockedSlotsFigure... modification){
            this.mod = modification;
        }

    }

    @Getter
    private final Integer[] slots;

    LockedSlotsFigure(Integer... slots){
        this(false, slots);
    }

    LockedSlotsFigure(boolean reversed, Integer... slots){
        if (!reversed) {
            this.slots = slots;
            return;
        }
        List<Integer> lockedSlots = IntStream.rangeClosed(0, 53).boxed().collect(Collectors.toList());
        for (Integer slot : slots){
            lockedSlots.remove(Integer.valueOf(slot));
        }
        this.slots = lockedSlots.toArray(new Integer[0]);
    }

    public static LockedSlotsFigure AUTO(LockedSlotsFigure.Raw rawFigure, int size){
        if (size == 0 || size % 9 != 0) throw new IllegalArgumentException("Menu size must be a multiple of 9.");
        return rawFigure.mod[size / 9 - 1];
    }

}

package com.bluup.manifestation.server.iota;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class UiDropdownIota extends Iota {
    private final Iota label;
    private final List<String> options;
    private final int selectedIndex;
    private final int depth;
    private final int size;

    public UiDropdownIota(Iota label, List<String> options, int selectedIndex) {
        super(ManifestationUiIotaTypes.UI_DROPDOWN, List.of(label, List.copyOf(options), selectedIndex));
        this.label = label;
        this.options = List.copyOf(options);
        this.selectedIndex = this.options.isEmpty()
            ? 0
            : Math.max(0, Math.min(selectedIndex, this.options.size() - 1));
        this.depth = label.depth() + 1;
        this.size = 1 + label.size();
    }

    public Iota getLabel() {
        return label;
    }

    public List<String> getOptions() {
        return options;
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    @Override
    public boolean isTruthy() {
        return true;
    }

    @Override
    protected boolean toleratesOther(Iota that) {
        if (!(that instanceof UiDropdownIota other)) {
            return false;
        }
        return Iota.tolerates(this.label, other.label)
            && this.options.equals(other.options)
            && this.selectedIndex == other.selectedIndex;
    }

    @Override
    public @NotNull Tag serialize() {
        CompoundTag out = new CompoundTag();
        out.put("label", IotaType.serialize(label));
        ListTag optionTags = new ListTag();
        for (String option : options) {
            optionTags.add(StringTag.valueOf(option));
        }
        out.put("options", optionTags);
        out.put("selected", IntTag.valueOf(selectedIndex));
        return out;
    }

    @Override
    public @Nullable Iterable<Iota> subIotas() {
        return List.of(label);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public int depth() {
        return depth;
    }
}
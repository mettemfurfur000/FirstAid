/*
 * FirstAid
 * Copyright (C) 2017-2024
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ichttt.mods.firstaid.common.damagesystem.distribution;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.api.distribution.IDamageDistributionAlgorithm;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nonnull;

public class DirectDamageDistributionAlgorithm implements IDamageDistributionAlgorithm {
    public static final Codec<DirectDamageDistributionAlgorithm> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    StringRepresentable.fromEnum(() -> EnumPlayerPart.VALUES).fieldOf("part").forGetter(o -> o.part),
                    Codec.BOOL.fieldOf("debuff").forGetter(o -> o.debuff)
            ).apply(instance, DirectDamageDistributionAlgorithm::new));
    private final EnumPlayerPart part;
    private final boolean debuff;

    public DirectDamageDistributionAlgorithm(EnumPlayerPart part, boolean debuff) {
        this.part = part;
        this.debuff = debuff;
    }

    @Override
    public float distributeDamage(float damage, @Nonnull Player player, @Nonnull DamageSource source, boolean addStat) {
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel == null) return 0F;
        return damageModel.getFromEnum(part).damage(damage, player, debuff);
    }

    @Override
    public Codec<DirectDamageDistributionAlgorithm> codec() {
        return CODEC;
    }
}

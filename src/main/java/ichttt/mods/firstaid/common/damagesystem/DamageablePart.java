package ichttt.mods.firstaid.common.damagesystem;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPartHealer;
import ichttt.mods.firstaid.api.debuff.IDebuff;
import ichttt.mods.firstaid.api.enums.EnumPlayerPart;
import ichttt.mods.firstaid.common.apiimpl.FirstAidRegistryImpl;
import ichttt.mods.firstaid.common.items.FirstAidItems;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Objects;

public class DamageablePart extends AbstractDamageablePart {
    private int maxHealth;
    @Nonnull
    private final IDebuff[] debuffs;
    private float absorption;

    public DamageablePart(int maxHealth, boolean canCauseDeath, @Nonnull EnumPlayerPart playerPart, @Nonnull IDebuff... debuffs) {
        super(maxHealth, canCauseDeath, playerPart);
        this.maxHealth = maxHealth;
        this.currentHealth = maxHealth;
        this.debuffs = debuffs;
    }

    @Override
    public float heal(float amount, @Nullable EntityPlayer player, boolean applyDebuff) {
        if (amount <= 0F)
            return 0F;
        float notFitting = Math.abs(Math.min(0F, maxHealth - (currentHealth + amount)));
        currentHealth = Math.min(maxHealth, currentHealth + amount);
        if (notFitting > 0) {
            float oldHealth = currentHealth;
            currentHealth = Math.min(currentHealth + notFitting, currentHealth);
            notFitting = notFitting - (currentHealth - oldHealth);
        }
        final float finalNotFitting = notFitting;
        if (applyDebuff) {
            Objects.requireNonNull(player, "Got null player with applyDebuff = true");
            Arrays.stream(debuffs).forEach(debuff -> debuff.handleHealing(amount - finalNotFitting, currentHealth / maxHealth, (EntityPlayerMP) player));
        }
        return notFitting;
    }

    @Override
    public float damage(float amount, @Nullable EntityPlayer player, boolean applyDebuff) {
        return damage(amount, player, applyDebuff, 0F);
    }

    @Override
    public float damage(float amount, @Nullable EntityPlayer player, boolean applyDebuff, float minHealth) {
        if (amount <= 0F)
            return 0F;
        if (minHealth > maxHealth)
            throw new IllegalArgumentException("Cannot damage part with minHealth " + minHealth + " while he has more max health (" + maxHealth + ")");
        float origAmount = amount;
        if (absorption > 0) {
            amount = Math.abs(Math.min(0, absorption - origAmount));
            absorption = Math.max(0, absorption - origAmount);
        }
        float notFitting = Math.abs(Math.min(minHealth, currentHealth - amount) - minHealth);
        currentHealth = Math.max(minHealth, currentHealth - amount);
        if (applyDebuff) {
            Objects.requireNonNull(player, "Got null player with applyDebuff = true");
            Arrays.stream(debuffs).forEach(debuff -> debuff.handleDamageTaken(origAmount - notFitting, currentHealth / maxHealth, (EntityPlayerMP) player));
        }
        return notFitting;
    }

    @Override
    public void tick(World world, EntityPlayer player, boolean tickDebuffs) {
        if (activeHealer != null) {
            if (activeHealer.tick()) {
                heal(1F, player, !world.isRemote);
            }
            if (activeHealer.hasFinished())
                activeHealer = null;
        }
        if (!world.isRemote && tickDebuffs)
            Arrays.stream(debuffs).forEach(debuff -> debuff.update(player, currentHealth / maxHealth));
    }

    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagCompound compound = new NBTTagCompound();
        compound.setFloat("health", currentHealth);
        if (FirstAid.scaleMaxHealth) compound.setInteger("maxHealth", maxHealth);
        if (absorption > 0F)
            compound.setFloat("absorption", absorption);
        if (activeHealer != null) {
            compound.setTag("healer", activeHealer.stack.serializeNBT());
            compound.setInteger("itemTicks", activeHealer.getTicksPassed());
            compound.setInteger("itemHeals", activeHealer.getHealsDone());
        }
        return compound;
    }

    @Override
    public void deserializeNBT(@Nullable NBTTagCompound nbt) {
        if (nbt == null)
            return;
        if (nbt.hasKey("maxHealth") && FirstAid.scaleMaxHealth) maxHealth = nbt.getInteger("maxHealth");
        currentHealth = Math.min(maxHealth, nbt.getFloat("health"));
        ItemStack stack = null;
        if (nbt.hasKey("healingItem"))
            stack = new ItemStack(nbt.getByte("healingItem") == 1 ? FirstAidItems.PLASTER : FirstAidItems.BANDAGE);
        else if (nbt.hasKey("healer")) stack = new ItemStack((NBTTagCompound) nbt.getTag("healer"));

        if (stack != null) {
            AbstractPartHealer healer = FirstAidRegistryImpl.INSTANCE.getPartHealer(stack);
            if (healer == null) FirstAid.logger.warn("Failed to lookup healer for item {}", stack.getItem());
            else activeHealer = healer.loadNBT(nbt.getInteger("itemTicks"), nbt.getInteger("itemHeals"));
        }
        if (nbt.hasKey("absorption"))
            absorption = nbt.getFloat("absorption");
        //kick constant debuffs active
        Arrays.stream(debuffs).forEach(debuff -> debuff.handleHealing(0F, currentHealth / maxHealth, null));
    }

    @Override
    public void setAbsorption(float absorption) {
        if (absorption > 4F && FirstAid.capMaxHealth) absorption = 4F;
        if (absorption > 32F) absorption = 32F;
        this.absorption = absorption;
        currentHealth = Math.min(maxHealth + absorption, currentHealth);
    }

    @Override
    public float getAbsorption() {
        return absorption;
    }

    @Override
    public void setMaxHealth(int maxHealth) {
        if (maxHealth > 12 && FirstAid.capMaxHealth) maxHealth = 12;
        if (maxHealth > 128) //Apply a max cap even if disabled - This is already OP and I know no use case where the limit might be reached
            maxHealth = 128;
        this.maxHealth = Math.max(2, maxHealth); //set 2 as a minimum
        this.currentHealth = Math.min(currentHealth, this.maxHealth);
    }

    @Override
    public int getMaxHealth() {
        return maxHealth;
    }
}

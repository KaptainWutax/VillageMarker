package kaptainwutax.villagemarker.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnType;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Timestamp;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.village.VillagerData;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Optional;

@Mixin(VillagerEntity.class)
public abstract class MixinVillagerEntity extends Entity {

	@Shadow public abstract Brain<VillagerEntity> getBrain();

	@Shadow public abstract VillagerData getVillagerData();

	private static int MODE_NONE = 0;
	private static int MODE_AI = 1;
	private static int MODE_TIMER = 2;
	private static int MODE_GOLEM_SPAWN = 3;

	private static int MODES_COUNT = 4;

	private static int VILLAGER_MODE = MODE_NONE;

	private static final Activity[] ACTIVITIES = {
			Activity.CORE,
			Activity.IDLE,
			Activity.WORK,
			Activity.PLAY,
			Activity.REST,
			Activity.MEET,
			Activity.PANIC,
			Activity.RAID,
			Activity.PRE_RAID,
			Activity.HIDE
	};

	private long lastInteractionTick = 0;

	public MixinVillagerEntity(EntityType<?> entityType, World world) {
		super(entityType, world);
	}

	@Inject(at = @At("HEAD"), method = "tick()V")
	public void tick(CallbackInfo callbackInfo) {
		if(!this.world.isClient) {
			this.setCustomName(this.getName());
		}
	}

	@Inject(at = @At("HEAD"), method = "interactMob")
	public boolean interactMob(PlayerEntity playerEntity, Hand hand, CallbackInfoReturnable callbackInfoReturnable) {
		long worldTime = this.world.getTime();

		if(!playerEntity.isSneaking() || playerEntity.world.isClient || this.lastInteractionTick == worldTime)return true;

		VILLAGER_MODE = (VILLAGER_MODE + 1) % MODES_COUNT;
		this.lastInteractionTick = worldTime;
		return true;
	}

	@Override
	public Text getName() {
		if(VILLAGER_MODE == MODE_NONE) {
			return null;
		} else if(VILLAGER_MODE == MODE_AI) {
			return new LiteralText(this.getCustomNameAI());
		} else if(VILLAGER_MODE == MODE_TIMER) {
			return new LiteralText(this.getCustomNameTimer());
		} else if(VILLAGER_MODE == MODE_GOLEM_SPAWN) {
			return new LiteralText(this.getCustomNameGolemSpawn());
		}

		return null;
	}

	private String getCustomNameAI() {
		StringBuilder name = new StringBuilder();

		name.append("Running Activities:");

		for(int i = 0; i < ACTIVITIES.length; i++) {
			Activity activity = ACTIVITIES[i];

			if(this.getBrain().hasActivity(activity)) {
				if(i != 0)name.append(",");
				name.append(" ");
				name.append(activity.getId().toUpperCase());
			}
		}

		return name.toString();
	}

	private String getCustomNameTimer() {
		StringBuilder name = new StringBuilder();

		Optional<Timestamp> sleepModule = this.getBrain().getOptionalMemory(MemoryModuleType.LAST_SLEPT);
		Optional<Timestamp> workModule = this.getBrain().getOptionalMemory(MemoryModuleType.LAST_WORKED_AT_POI);

		name.append("Sleep: ");
		long lastSlept = 0L;

		if(sleepModule.isPresent()) {
			lastSlept = 24000L - this.world.getTime() + sleepModule.get().getTime();
		}

		name.append(lastSlept <= 0L ? "Sleep! " : lastSlept + " ");

		name.append("| Work: ");
		long lastWorked = 0L;

		if(workModule.isPresent()) {
			lastWorked = 36000L - this.world.getTime() + workModule.get().getTime();
		}

		name.append(lastWorked <= 0L ? "Work! " : lastWorked + " ");

		return name.toString();
	}

	private String getCustomNameGolemSpawn() {
		StringBuilder name = new StringBuilder();

		Optional<Long> golemModule = this.getBrain().getOptionalMemory(MemoryModuleType.GOLEM_LAST_SEEN_TIME);

		name.append("Timer: ");

		long lastGolem = 0L;

		if(golemModule.isPresent()) {
			lastGolem = 600 - this.world.getTime() + golemModule.get();
		}

		name.append(lastGolem > 0 ? lastGolem + " " : "Spawn! ");

		name.append("| Panic: ");
		boolean panicking = this.getBrain().hasActivity(Activity.PANIC);
		name.append(panicking ? "TRUE " : "FALSE ");

		name.append("| Group: ");
		Box groupBox = this.getBoundingBox().expand(10.0D, 10.0D, 10.0D);
		List<VillagerEntity> group = this.world.getEntities(VillagerEntity.class, groupBox);
		name.append(group.size()).append(" ");

		name.append("| Spaces: ");
		name.append(this.getSpawningSpaces(new BlockPos(this)));

		return name.toString();
	}

	private int getSpawningSpaces(BlockPos origin) {
		int[] countMap = new int[13];

		for(int x = -8; x < 8; x++) {
			for(int z = -8; z < 8; z++) {
				for(int y = 6; y >= -6; --y) {
					BlockPos spawnPos = origin.add(x, y, z);

					if((this.world.getBlockState(spawnPos).isAir() ||
							this.world.getBlockState(spawnPos).getMaterial().isLiquid()) &&
							this.world.getBlockState(spawnPos.down()).getMaterial().blocksLight()) {
						;
					} else if(y == 6) {
						;
					} else {
						continue;
					}

					IronGolemEntity golem = EntityType.IRON_GOLEM.create(this.world, null, null, null, spawnPos, SpawnType.MOB_SUMMONED, false, false);

					if(golem != null && golem.canSpawn(this.world, SpawnType.MOB_SUMMONED) && golem.canSpawn(this.world)) {
						countMap[y + 6]++;
					}

					if(golem != null) {
						golem.remove();
					}
				}
			}
		}

		int spaces = 0;

		for(int count : countMap) {
			spaces += count;
		}

		return spaces;
	}

	@Override
	public boolean isCustomNameVisible() {
		return true;
	}

}


package net.narutomod.entity;

import net.narutomod.item.ItemSenbonArm;
import net.narutomod.item.ItemPoisonSenbon;
import net.narutomod.item.ItemScrollHiruko;
import net.narutomod.item.ItemScroll3rdKazekage;
import net.narutomod.procedure.ProcedureUtils;
import net.narutomod.procedure.ProcedureSync;
import net.narutomod.ModConfig;
import net.narutomod.NarutomodModVariables;
import net.narutomod.ElementsNarutomodMod;

import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.common.MinecraftForge;

import net.minecraft.world.BossInfo;
import net.minecraft.world.BossInfoServer;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.World;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;
import net.minecraft.util.EntitySelectors;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest2;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.IRangedAttackMob;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.ModelBox;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.network.datasync.DataSerializers;

import javax.annotation.Nullable;
import javax.vecmath.Vector3f;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.model.ModelBase;
import net.minecraft.util.EnumFacing;

@ElementsNarutomodMod.ModElement.Tag
public class EntitySasori extends ElementsNarutomodMod.ModElement {
	public static final int ENTITYID = 437;
	public static final int ENTITYID_RANGED = 438;

	public EntitySasori(ElementsNarutomodMod instance) {
		super(instance, 868);
	}

	@Override
	public void initElements() {
		elements.entities.add(() -> EntityEntryBuilder.create().entity(EntityCustom.class)
		 .id(new ResourceLocation("narutomod", "sasori"), ENTITYID).name("sasori").tracker(64, 3, true).egg(-16777216, -65485).build());
		elements.entities.add(() -> EntityEntryBuilder.create().entity(EntityCore.class)
		 .id(new ResourceLocation("narutomod", "sasori_core"), ENTITYID_RANGED).name("sasori_core").tracker(64, 3, true).build());
	}

	@Override
	public void init(FMLInitializationEvent event) {
		MinecraftForge.EVENT_BUS.register(new EntityCustom.AttackHook());
	}

	public static class EntityCustom extends EntityNinjaMob.Base implements IMob, IRangedAttackMob {
		private static final DataParameter<Integer> ROBE_OFF_TICKS = EntityDataManager.<Integer>createKey(EntityCustom.class, DataSerializers.VARINT);
		private final BossInfoServer bossInfo = new BossInfoServer(this.getDisplayName(), BossInfo.Color.RED, BossInfo.Overlay.PROGRESS);
		private final int BLOCKING_CD = 30;
		private final int JUTSU_CD = 300;
		private final int SANDGATHERING_CD = 400;
		private final int SANDBULLET_CD = 200;
		private final double chakra4elementals = 200d;
		private int lastBlockTime;
		private EntityCore coreEntity;
		private EntityPuppetHiruko.EntityCustom hirukoEntity;
		private EntityPuppet3rdKazekage.EntityCustom thirdEntity;
		private int senbonArmShootCount;
		private final int bladesOpenTime = 60;
		private boolean thirdScrollUsed;
		private int lastSandBulletTime;
		private int lastSandGatheringTime;
		private int lastElementalJutsuTime;
		private int realFightStage;
		private int fireImmuneTicks;

		public EntityCustom(World world) {
			super(world, 60, 7000d);
			this.setSize(0.525f, 1.75f);
		}

		@Override
		public IEntityLivingData onInitialSpawn(DifficultyInstance difficulty, @Nullable IEntityLivingData livingdata) {
			this.setItemToInventory(new ItemStack(ItemPoisonSenbon.block), 0);
			this.setItemToInventory(new ItemStack(ItemSenbonArm.block), 1);
			this.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, new ItemStack(ItemScroll3rdKazekage.block, 1));
			return super.onInitialSpawn(difficulty, livingdata);
		}

		@Override
		protected void entityInit() {
			super.entityInit();
			this.dataManager.register(ROBE_OFF_TICKS, Integer.valueOf(-1));
		}

		private void setRobeOffTicks(int ticks) {
			this.dataManager.set(ROBE_OFF_TICKS, Integer.valueOf(ticks));
		}
	
		public int getRobeOffTicks() {
			return ((Integer)this.getDataManager().get(ROBE_OFF_TICKS)).intValue();
		}

		protected void takeOffRobe(boolean off) {
			this.setRobeOffTicks(off ? 0 : -1);
		}

		protected boolean isRobeOff() {
			return this.getRobeOffTicks() >= 0;
		}

		@Override
		protected void applyEntityAttributes() {
			super.applyEntityAttributes();
			this.getEntityAttribute(SharedMonsterAttributes.ARMOR).setBaseValue(100D);
			this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.5D);
			this.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE).setBaseValue(10D);
		}

		@Override
		protected void dropLoot(boolean wasRecentlyHit, int lootingModifier, DamageSource source) {
			if (this.rand.nextFloat() < 0.5f) {
				ItemStack stack = new ItemStack(ItemScrollHiruko.block);
				stack.setItemDamage(10 + this.rand.nextInt((int)EntityPuppetHiruko.EntityCustom.MAXHEALTH - 10));
				this.entityDropItem(stack, 0.0f);
			}
			if (this.rand.nextFloat() < 0.25f) {
				ItemStack stack = new ItemStack(ItemScroll3rdKazekage.block);
				stack.setItemDamage(10 + this.rand.nextInt((int)EntityPuppet3rdKazekage.EntityCustom.MAXHEALTH - 10));
				this.entityDropItem(stack, 0.0f);
			}
		}

		@Override
		protected void initEntityAI() {
			super.initEntityAI();
			this.tasks.addTask(0, new EntityAISwimming(this));
			this.tasks.addTask(1, new EntityNinjaMob.AIAttackRangedTactical(this, 0.5d, 30, 12.0F) {
				@Override
				public boolean shouldExecute() {
					return super.shouldExecute() && EntityCustom.this.isRidingHiruko();
				}
			});
			this.tasks.addTask(2, new EntityNinjaMob.AIAttackRangedTactical(this, 1.0d, JUTSU_CD, 30.0F) {
				@Override
				public boolean shouldExecute() {
					return super.shouldExecute() && !EntityCustom.this.isRidingHiruko();
				}
			});
			this.tasks.addTask(3, new EntityAIWatchClosest2(this, EntityPlayer.class, 15.0F, 1.0F));
			this.tasks.addTask(4, new EntityAIWander(this, 0.3));
			this.tasks.addTask(5, new EntityAILookIdle(this));
			this.targetTasks.addTask(1, new EntityAIHurtByTarget(this, false));
			this.targetTasks.addTask(2, new EntityPuppet.AIRidingHurtByTarget(this));
			this.targetTasks.addTask(3, new EntityAINearestAttackableTarget(this, EntityPlayer.class, 10, true, false,
					new Predicate<EntityPlayer>() {
						public boolean apply(@Nullable EntityPlayer p_apply_1_) {
							return p_apply_1_ != null && EntityBijuManager.isJinchuriki(p_apply_1_);
						}
					}));
			//this.targetTasks.addTask(4, new EntityAINearestAttackableTarget(this, EntityMob.class, false, false));
		}

		@Override
		protected void updateAITasks() {
			super.updateAITasks();
			ItemStack offStack = this.getHeldItemOffhand();
			ItemStack inv1Stack = this.getItemFromInventory(1);
			if (this.getAttackTarget() != null) {
				if (this.isRidingHiruko() && this.hirukoEntity.getHealth() < this.hirukoEntity.getMaxHealth() * 0.5f
			 	 && inv1Stack.getItem() == ItemSenbonArm.block && this.rand.nextFloat() < 0.01f) {
					this.swapWithInventory(EntityEquipmentSlot.OFFHAND, 1);
			 	} else if (!this.isRidingHiruko() && !this.thirdScrollUsed) {
			 		if (inv1Stack.getItem() == ItemScroll3rdKazekage.block) {
			 			this.swapWithInventory(EntityEquipmentSlot.OFFHAND, 1);
			 		} else if (offStack.getItem() == ItemScroll3rdKazekage.block) {
			 			if (this.onGround) {
					 		this.swingArm(EnumHand.OFF_HAND);
							BlockPos pos = new BlockPos(this.getAttackTarget().getPositionVector()
							 .subtract(this.getPositionEyes(1f)).normalize().scale(1.5d).add(this.getPositionEyes(1f)));
							for ( ; this.world.isAirBlock(pos); pos = pos.down()) ;
							ItemScroll3rdKazekage.RangedItem.useItem(offStack, this, pos);
							this.standStillFor(30);
							this.thirdScrollUsed = true;
			 			}
			 		} else {
			 			this.thirdScrollUsed = true;
			 		}
				} else if (this.isThirdSummoned() && offStack.getItem() == ItemScroll3rdKazekage.block) {
					if (this.isHandActive()) {
						if (offStack.getMaxItemUseDuration() - this.getItemInUseCount() >= 80) {
							this.stopActiveHand();
						}
					} else if (ItemScroll3rdKazekage.GATHERING.jutsu.isActivated(this.thirdEntity)
					 && (this.ticksExisted - this.lastSandGatheringTime) % 80 == 50) {
						((ItemScroll3rdKazekage.RangedItem)offStack.getItem()).executeJutsu(offStack, this, 1f);
					}
				} else if (((offStack.getItem() != ItemScroll3rdKazekage.block && inv1Stack.getItem() != ItemScroll3rdKazekage.block)
				 || (this.thirdEntity != null && !this.thirdEntity.isEntityAlive())) && !this.isRidingHiruko() && !this.isRobeOff()) {
					this.takeOffRobe(true);
					this.resetActiveHand();
					if (offStack.getItem() == ItemScroll3rdKazekage.block) {
						this.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, ItemStack.EMPTY);
					}
				}
			} else {
				this.takeOffRobe(false);
				if (this.isRidingHiruko()) {
					this.hirukoEntity.takeRobeOff(false);
				} else if (this.isThirdSummoned() && offStack.getItem() == ItemScroll3rdKazekage.block) {
					this.resetActiveHand();
					ItemScroll3rdKazekage.RangedItem.interactWithEntity(offStack, this, this.thirdEntity);
					this.thirdScrollUsed = false;
					this.thirdEntity = null;
				}
			}
		}

		@Override
		public boolean isOnSameTeam(Entity entityIn) {
			return super.isOnSameTeam(entityIn) || EntityNinjaMob.TeamAkatsuki.contains(entityIn.getClass());
		}

		@Override
		public void dismountEntity(Entity entityIn) {
			if (entityIn.equals(this.hirukoEntity)) {
				Vec3d vec = this.getAttackTarget() != null
				 ? this.getPositionVector().subtract(this.getAttackTarget().getPositionVector()).normalize().scale(0.5d)
				 : this.getLookVec().scale(-0.5d);
				this.motionX = vec.x;
				this.motionZ = vec.z;
				this.motionY = 0.62d;
				this.isAirBorne = true;
				this.fallDistance = 0.0f;
			} else {
				super.dismountEntity(entityIn);
			}
		}

		@Override
		public boolean attackEntityAsMob(Entity entityIn) {
			if (this.isRidingHiruko()) {
				return this.hirukoEntity.attackEntityAsMob(entityIn);
			}
			return super.attackEntityAsMob(entityIn);
		}

		@Override
		public boolean attackEntityFrom(DamageSource source, float amount) {
			if (this.isRidingHiruko()) {
				this.hirukoEntity.attackEntityFrom(source, amount);
				return false;
			}
			if (source == DamageSource.FALL) {
				return false;
			}
			if (this.equals(source.getTrueSource())) {
				return false;
			}
			if (source.isExplosion() && source.getTrueSource() != null && source.getTrueSource().equals(this.thirdEntity)) {
				return false;
			}
			return super.attackEntityFrom(source, amount);
		}

		@Override
		public void setSwingingArms(boolean swingingArms) {
			if (this.isThirdSummoned()) {
				ProcedureSync.EntityNBTTag.setAndSync(this, NarutomodModVariables.forceBowPose, swingingArms);
			}
		}

		@Override
		public void attackEntityWithRangedAttack(EntityLivingBase target, float flval) {
			ItemStack stack = this.getHeldItemOffhand(); 
			if (this.isRidingHiruko()) {
				double d = this.getDistance(target);
				if (d > 8.0d && stack.getItem() == ItemSenbonArm.block) {
					Vec3d vec = target.getPositionEyes(1f).subtract(this.getPositionVector());
					ItemSenbonArm.RangedItem.shootItem(this.hirukoEntity, vec.x, vec.y + d * 0.2d, vec.z, 0.5f);
					if (++this.senbonArmShootCount > 1) {
						this.setItemStackToSlot(EntityEquipmentSlot.OFFHAND, ItemStack.EMPTY);
					}
					this.swapWithInventory(EntityEquipmentSlot.OFFHAND, 1);
				} else if (d < 7.0d) {
					this.swingArm(EnumHand.MAIN_HAND);
					this.attackEntityAsMob(target);
				} else {
					Vec3d vec = target.getPositionEyes(1f);
					for (int i = 0; i < 20; i++) {
						ItemPoisonSenbon.spawnArrow(this.hirukoEntity, vec);
					}
				}
			} else if (this.isThirdSummoned() && stack.getItem() == ItemScroll3rdKazekage.block
			 && !ItemScroll3rdKazekage.GATHERING.jutsu.isActivated(this.thirdEntity)) {
				if (this.ticksExisted - this.lastSandGatheringTime > SANDGATHERING_CD && this.rand.nextFloat() < 0.25f) {
					((ItemScroll3rdKazekage.RangedItem)stack.getItem()).setCurrentJutsu(stack, ItemScroll3rdKazekage.GATHERING);
					((ItemScroll3rdKazekage.RangedItem)stack.getItem()).executeJutsu(stack, this, 1f);
					this.lastSandGatheringTime = this.ticksExisted;
				} else if (this.ticksExisted - this.lastSandBulletTime > SANDBULLET_CD && !this.isHandActive()) {
					((ItemScroll3rdKazekage.RangedItem)stack.getItem()).setCurrentJutsu(stack, ItemScroll3rdKazekage.SANDBULLET);
					this.setActiveHand(EnumHand.OFF_HAND);
					this.lastSandBulletTime = this.ticksExisted;
				}
			} else if (this.isRobeOff() && this.ticksExisted - this.lastElementalJutsuTime > 150) {
				if (this.rand.nextFloat() <= 0.5f && this.consumeChakra(this.chakra4elementals)) {
					this.fireImmuneTicks = 300;
					new EntityFirestream.EC.Jutsu2().createJutsu(this, 25.0f, 200);
					this.lastElementalJutsuTime = this.ticksExisted;
				} else if (this.rand.nextFloat() <= 0.5f && this.consumeChakra(this.chakra4elementals)) {
					new EntityWaterStream.EC.Jutsu().createJutsu(this, 25.0f, 200);
					this.lastElementalJutsuTime = this.ticksExisted;
				} else if (this.consumeChakra(this.chakra4elementals)) {
					new EntityFutonVacuum.EC.Jutsu().createJutsu(this, 25.0f, 200);
					this.lastElementalJutsuTime = this.ticksExisted;
				}
			}
		}

		@Override
		public void travel(float strafe, float vertical, float forward) {
			if (this.isServerWorld() && this.isRidingHiruko() && this.motionY > 0.01d) {
				this.hirukoEntity.motionY = this.motionY * 0.9d;
			}
			super.travel(strafe, vertical, forward);
		}

		@Override
		protected void collideWithNearbyEntities() {
			if (this.getRobeOffTicks() > this.bladesOpenTime) {
				for (Entity entity : this.world.getEntitiesInAABBexcluding(this, this.getEntityBoundingBox().grow(2.4d, 0d, 2.4d),
					Predicates.and(EntitySelectors.getTeamCollisionPredicate(this), new Predicate<Entity>() {
						@Override
						public boolean apply(@Nullable Entity p_apply_1_) {
							return p_apply_1_ instanceof EntityLivingBase;
						}
					}))) {
					entity.attackEntityFrom(DamageSource.causeThornsDamage(this), 10f + this.rand.nextFloat() * 8f);
				}
			}
			super.collideWithNearbyEntities();
		}

		@Override
		public boolean getCanSpawnHere() {
			return super.getCanSpawnHere()
			 && this.world.getEntitiesWithinAABB(EntityCustom.class, this.getEntityBoundingBox().grow(128.0D)).isEmpty();
			 //&& this.rand.nextInt(5) == 0;
		}

		@Override
		public void addTrackingPlayer(EntityPlayerMP player) {
			super.addTrackingPlayer(player);
			if (ModConfig.AGGRESSIVE_BOSSES) {
				this.setAttackTarget(player);
			}
		}

		@Override
		public void removeTrackingPlayer(EntityPlayerMP player) {
			super.removeTrackingPlayer(player);
			if (this.bossInfo.getPlayers().contains(player)) {
				this.bossInfo.removePlayer(player);
			}
		}

		private void trackAttackedPlayers() {
			Entity entity = this.getAttackingEntity();
			if (entity instanceof EntityPlayerMP || (entity = (ModConfig.AGGRESSIVE_BOSSES ? this.getLastAttackedEntity() : this.getAttackTarget())) instanceof EntityPlayerMP) {
				this.bossInfo.addPlayer((EntityPlayerMP) entity);
			}
		}

		@Override
		public void onUpdate() {
			if (this.hirukoEntity == null) {
				if (this.getRidingEntity() instanceof EntityPuppetHiruko.EntityCustom) {
					this.hirukoEntity = (EntityPuppetHiruko.EntityCustom)this.getRidingEntity();
				} else if (!this.world.isRemote) {
					this.hirukoEntity = new EntityPuppetHiruko.EntityCustom(this, this.posX, this.posY, this.posZ);
					this.hirukoEntity.setAkatsuki(true);
					this.world.spawnEntity(this.hirukoEntity);
					
					Vec3d vec = this.getLookVec();
					this.coreEntity = new EntityCore(this);
					this.coreEntity.shoot(vec.x, vec.y, vec.z, 0.9f, 0.1f);
					this.world.spawnEntity(this.coreEntity);
				}
			}
			ItemStack stack = this.getHeldItemOffhand();
			if (!this.world.isRemote) {
				if (this.isRidingHiruko()) {
					if (this.width < this.hirukoEntity.width - 0.01f) {
						this.setSize(this.hirukoEntity.width - 0.01f, this.hirukoEntity.height - 0.01f);
					}
				} else if (this.width > 0.525f) {
					this.setSize(0.525f, 1.75f);
				}
			} else if (this.isRidingHiruko()) {
				this.hirukoEntity.raiseLeftArm(stack.getItem() == ItemSenbonArm.block);
			}
			if (this.thirdEntity == null && stack.getItem() == ItemScroll3rdKazekage.block) {
				this.thirdEntity = ((ItemScroll3rdKazekage.RangedItem)stack.getItem()).getPuppetEntity(stack, this.world);
			}
			this.clearActivePotions();
			super.onUpdate();
			if (!this.world.isRemote && this.isRidingHiruko() && this.ticksExisted > this.lastBlockTime + 20) {
				this.hirukoEntity.blockAttack(false);
			}
			this.isImmuneToFire = this.fireImmuneTicks > 0;
			if (this.fireImmuneTicks > 0) {
				--this.fireImmuneTicks;
			}
			int robeOffTicks = this.getRobeOffTicks();
			if (!this.world.isRemote && !this.isRidingHiruko() && robeOffTicks >= 0) {
				this.setRobeOffTicks(++robeOffTicks);
			}
			this.trackAttackedPlayers();
			this.bossInfo.setPercent(this.getHealth() / this.getMaxHealth());
		}

		@Override
		public void setDead() {
			super.setDead();
			if (!this.world.isRemote && this.hirukoEntity != null && this.hirukoEntity.isEntityAlive()) {
				this.hirukoEntity.setDead();
			}
		}

		private boolean isRidingHiruko() {
			return this.hirukoEntity != null && this.hirukoEntity.isEntityAlive() && this.hirukoEntity.equals(this.getRidingEntity());
		}

		private boolean isThirdSummoned() {
			return this.thirdEntity != null && this.thirdEntity.isEntityAlive();
		}

		@Override
		public double getYOffset() {
			return -0.35d;
		}

		public static class AttackHook {
			@SubscribeEvent
			public void onAttacked(LivingAttackEvent event) {
				if (event.getEntityLiving() instanceof EntityPuppetHiruko.EntityCustom
				 && event.getEntityLiving().getControllingPassenger() instanceof EntityCustom
				 && !event.getSource().isUnblockable()) {
					EntityPuppetHiruko.EntityCustom hiruko = (EntityPuppetHiruko.EntityCustom)event.getEntityLiving();
					EntityCustom sasori = (EntityCustom)event.getEntityLiving().getControllingPassenger();
					if (sasori.ticksExisted > sasori.lastBlockTime + sasori.BLOCKING_CD) {
						hiruko.blockAttack(true);
						sasori.lastBlockTime = sasori.ticksExisted;
					}
				}
			}
		}
	}

	public static class EntityCore extends EntityThrowable {
		public EntityCore(World worldIn) {
			super(worldIn);
			this.setSize(0.3f, 0.25f);
		}

		public EntityCore(EntityLivingBase throwerIn) {
			super(throwerIn.world, throwerIn);
			this.setSize(0.3f, 0.25f);
		}

		@Override
		protected float getGravityVelocity() {
			return 0.05f;
		}

		@Override
		protected void onImpact(RayTraceResult result) {
			if (ProcedureUtils.getVelocity(this) < 0.1d) {
				BlockPos blockpos = result.getBlockPos();
				ReflectionHelper.setPrivateValue(EntityThrowable.class, this, blockpos.getX(), 0); //this.xTile = blockpos.getX();
				ReflectionHelper.setPrivateValue(EntityThrowable.class, this, blockpos.getY(), 1); // this.yTile = blockpos.getY();
				ReflectionHelper.setPrivateValue(EntityThrowable.class, this, blockpos.getZ(), 2); // this.zTile = blockpos.getZ();
		        IBlockState iblockstate = this.world.getBlockState(blockpos);
		        ReflectionHelper.setPrivateValue(EntityThrowable.class, this, iblockstate.getBlock(), 3); //this.inTile = iblockstate.getBlock();
				//ReflectionHelper.setPrivateValue(EntityThrowable.class, this, 900, 8); // this.ticksInGround = 900;
		        this.motionX = 0.0d;
		        this.motionY = 0.0d;
		        this.motionZ = 0.0d;
		        this.posX = result.hitVec.x;
		        this.posY = result.hitVec.y;
		        this.posZ = result.hitVec.z;
		        this.inGround = true;
		        //if (iblockstate.getMaterial() != Material.AIR) {
		         //   iblockstate.getBlock().onEntityCollidedWithBlock(this.world, blockpos, iblockstate, this);
		        //}
			} else if (result.entityHit != null) {
				if (!result.entityHit.equals(this.thrower)) {
					this.motionX *= -0.4d;
					this.motionY *= -0.4d;
					this.motionZ *= -0.4d;
			        this.posX = result.hitVec.x;
			        this.posY = result.hitVec.y;
			        this.posZ = result.hitVec.z;
				}
			} else {
				this.motionX *= 0.4d;
				this.motionY *= 0.4d;
				this.motionZ *= 0.4d;
		        this.posX = result.hitVec.x;
		        this.posY = result.hitVec.y;
		        this.posZ = result.hitVec.z;
				if (result.sideHit.getAxis() == EnumFacing.Axis.X) {
					this.motionX *= -0.8d;
				}
				if (result.sideHit.getAxis() == EnumFacing.Axis.Y) {
					this.motionY *= -0.8d;
				}
				if (result.sideHit.getAxis() == EnumFacing.Axis.Z) {
					this.motionZ *= -0.8d;
				}
			}
		}
	}

	@Override
	public void preInit(FMLPreInitializationEvent event) {
		new Renderer().register();
	}

	public static class Renderer extends EntityRendererRegister {
		@SideOnly(Side.CLIENT)
		@Override
		public void register() {
			RenderingRegistry.registerEntityRenderingHandler(EntityCustom.class, renderManager -> new RenderCustom(renderManager));
			RenderingRegistry.registerEntityRenderingHandler(EntityCore.class, renderManager -> new RenderCore(renderManager));
		}

		@SideOnly(Side.CLIENT)
		public class RenderCustom extends EntityNinjaMob.RenderBase<EntityCustom> {
			private final ResourceLocation texture = new ResourceLocation("narutomod:textures/sasori2.png");

			public RenderCustom(RenderManager renderManagerIn) {
				super(renderManagerIn, new ModelSasori());
			}

			@Override
			protected void renderLayers(EntityCustom entity, float f0, float f1, float f2, float f3, float f4, float f5, float f6) {
				if (!entity.isInvisible()) {
					super.renderLayers(entity, f0, f1, f2, f3, f4, f5, f6);
				}
			}

			@Override
			protected void preRenderCallback(EntityCustom entity, float partialTickTime) {
				float f = 0.0625f * 14;
				GlStateManager.scale(f, f, f);
			}

			@Override
			protected ResourceLocation getEntityTexture(EntityCustom entity) {
				return this.texture;
			}
		}

		@SideOnly(Side.CLIENT)
		public class RenderCore extends Render<EntityCore> {
			private final ResourceLocation texture = new ResourceLocation("narutomod:textures/sasori_core.png");
			private final ModelSasoriCore model = new ModelSasoriCore();
	
			public RenderCore(RenderManager renderManager) {
				super(renderManager);
				this.shadowSize = 0.1F;
			}
	
			@Override
			public void doRender(EntityCore entity, double x, double y, double z, float yaw, float pt) {
				this.bindEntityTexture(entity);
				GlStateManager.pushMatrix();
				GlStateManager.translate((float) x, (float) y + 0.25f, (float) z);
				GlStateManager.rotate(-entity.prevRotationYaw - (entity.rotationYaw - entity.prevRotationYaw) * pt, 0.0F, 1.0F, 0.0F);
				GlStateManager.rotate(entity.prevRotationPitch + (entity.rotationPitch - entity.prevRotationPitch) * pt - 180.0F, 1.0F, 0.0F, 0.0F);
				this.model.render(entity, 0.0F, 0.0F, pt + entity.ticksExisted, 0.0F, 0.0F, 0.0625F);
				GlStateManager.popMatrix();
			}
	
			@Override
			protected ResourceLocation getEntityTexture(EntityCore entity) {
				return this.texture;
			}
		}

		@SideOnly(Side.CLIENT)
		public class ModelSasori extends EntityNinjaMob.ModelNinja {
			//private final ModelRenderer bipedHead;
			//private final ModelRenderer bipedHeadwear;
			private final ModelRenderer robeHead;
			//private final ModelRenderer bipedBody;
			private final ModelRenderer robeBody;
			private final ModelRenderer scrolls;
			private final ModelRenderer backBladesRight;
			private final ModelRenderer bone;
			private final ModelRenderer[] blade = new ModelRenderer[10];
			private final ModelRenderer backBladesLeft;
			private final ModelRenderer bone12;
			private final ModelRenderer core;
			//private final ModelRenderer bipedRightArm;
			private final ModelRenderer robeRightArm;
			private final ModelRenderer gunRight;
			//private final ModelRenderer bipedLeftArm;
			private final ModelRenderer robeLeftArm;
			private final ModelRenderer gunLeft;
			//private final ModelRenderer bipedRightLeg;
			private final ModelRenderer robeRightLeg;
			//private final ModelRenderer bipedLeftLeg;
			private final ModelRenderer robeLeftLeg;
			private final Vector3f[] backBladesRightPreset = { new Vector3f(0.0F, 0.2618F, 1.309F), new Vector3f(0.0F, 0.5236F, -0.2618F) };
			private final Vector3f[] backBladesLeftPreset = { new Vector3f(0.0F, -0.2618F, -1.309F), new Vector3f(0.0F, -0.5236F, 0.2618F) };
			private final float[][] bladePresetZ = { { -3.1416F, -0.5236F }, { -3.098F, 0.0873F }, { -3.0543F, 0.6981F }, { -3.0107F, 1.309F }, { -2.9671F, 1.9199F }, { 3.1398F, 0.5236F }, { 3.098F, -0.0873F }, { 3.0543F, -0.6981F }, { 3.0107F, -1.309F }, { 2.9671F, -1.9199F } };
			private final float[][] bipedHeadPreset = { { 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F }, { -5.0F, 21.0F, 26.0F, -1.5708F, 0.0F, 1.0908F } };
			private final float[][] bipedBodyPreset = { { 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F }, { 10.0F, 22.0F, 1.0F, -1.5708F, 0.5236F, 0.0F } };
			private final float[][] bipedRightArmPreset = { { -5.0F, 2.5F, 0.0F, 0.0F, 0.0F, 0.0F }, { -22.0F, 22.5F, 19.0F, 0.8727F, 0.0F, 1.5708F } };
			private final float[][] bipedLeftArmPreset = { { 5.0F, 2.5F, 0.0F, 0.0F, 0.0F, 0.0F }, { 18.0F, 27.5F, 12.0F, 1.5708F, -1.2217F, 3.1416F } };
			private final float[][] bipedRightLegPreset = { { -1.9F, 12.0F, 0.0F, 0.0F, 0.0F, 0.0F }, { -15.9F, 22.0F, -10.0F, -1.5708F, 0.829F, 0.0F } };
			private final float[][] bipedLeftLegPreset = { { 1.9F, 12.0F, 0.0F, 0.0F, 0.0F, 0.0F }, { 19.9F, 22.0F, 0.0F, -1.5708F, -1.309F, 0.0F } };
			
			public ModelSasori() {
				textureWidth = 64;
				textureHeight = 64;
				bipedHead = new ModelRenderer(this);
				bipedHead.setRotationPoint(0.0F, 0.0F, 0.0F);
				setRotationAngle(bipedHead, -0.1047F, 0.0873F, 0.0F);
				bipedHead.cubeList.add(new ModelBox(bipedHead, 0, 0, -4.0F, -8.0F, -4.0F, 8, 8, 8, 0.0F, false));
				bipedHead.cubeList.add(new ModelBox(bipedHead, 32, 0, -4.0F, -8.0F, -4.0F, 8, 8, 8, 0.3F, false));
				bipedHeadwear = new ModelRenderer(this);
				bipedHeadwear.setRotationPoint(0.0F, 0.0F, 0.0F);
				setRotationAngle(bipedHeadwear, -0.1047F, 0.0873F, 0.0F);
				robeHead = new ModelRenderer(this);
				robeHead.setRotationPoint(0.0F, 0.0F, 0.0F);
				bipedHeadwear.addChild(robeHead);
				robeHead.cubeList.add(new ModelBox(robeHead, 32, 54, -4.0F, -2.0F, -4.0F, 8, 2, 8, 0.65F, false));
				bipedBody = new ModelRenderer(this);
				bipedBody.setRotationPoint(0.0F, 0.0F, 0.0F);
				bipedBody.cubeList.add(new ModelBox(bipedBody, 16, 16, -4.0F, 0.0F, -2.0F, 8, 12, 4, 0.0F, false));
				robeBody = new ModelRenderer(this);
				robeBody.setRotationPoint(0.0F, 24.0F, 0.0F);
				bipedBody.addChild(robeBody);
				robeBody.cubeList.add(new ModelBox(robeBody, 16, 32, -4.0F, -24.0F, -2.0F, 8, 12, 4, 0.5F, false));
				scrolls = new ModelRenderer(this);
				scrolls.setRotationPoint(0.0F, 22.0F, 0.0F);
				bipedBody.addChild(scrolls);
				scrolls.cubeList.add(new ModelBox(scrolls, 24, 0, -3.0F, -21.0F, 2.0F, 6, 2, 2, 0.0F, false));
				scrolls.cubeList.add(new ModelBox(scrolls, 24, 0, -3.0F, -18.0F, 2.0F, 6, 2, 2, 0.0F, false));
				scrolls.cubeList.add(new ModelBox(scrolls, 24, 0, -3.0F, -15.0F, 2.0F, 6, 2, 2, 0.0F, false));
				scrolls.cubeList.add(new ModelBox(scrolls, 0, 20, -2.0F, -21.175F, 1.275F, 4, 9, 3, 0.0F, false));
				backBladesRight = new ModelRenderer(this);
				backBladesRight.setRotationPoint(-3.2F, 6.0F, 3.0F);
				bipedBody.addChild(backBladesRight);
				setRotationAngle(backBladesRight, backBladesRightPreset[0].x, backBladesRightPreset[0].y, backBladesRightPreset[0].z); //setRotationAngle(backBladesRight, 0.0F, 0.5236F, -0.2618F);
				backBladesRight.cubeList.add(new ModelBox(backBladesRight, 0, 0, -1.8F, 0.0F, -1.0F, 2, 1, 1, 0.1F, false));
				backBladesRight.cubeList.add(new ModelBox(backBladesRight, 0, 0, -3.8F, 0.0F, -1.0F, 2, 1, 1, 0.1F, false));
				backBladesRight.cubeList.add(new ModelBox(backBladesRight, 0, 0, -5.8F, 0.0F, -1.0F, 2, 1, 1, 0.1F, false));
				backBladesRight.cubeList.add(new ModelBox(backBladesRight, 0, 0, -7.8F, 0.0F, -1.0F, 2, 1, 1, 0.1F, false));
				backBladesRight.cubeList.add(new ModelBox(backBladesRight, 0, 0, -9.8F, 0.0F, -1.0F, 2, 1, 1, 0.1F, false));
				bone = new ModelRenderer(this);
				bone.setRotationPoint(-8.8F, 0.5F, -0.5F);
				backBladesRight.addChild(bone);
				setRotationAngle(bone, 0.0F, -1.0472F, 0.0F);
				for (int i = 0; i < blade.length / 2; i++) {
					blade[i] = new ModelRenderer(this);
					blade[i].setRotationPoint(0.0F, 0.0F, 0.5F);
					bone.addChild(blade[i]);
					setRotationAngle(blade[i], 0.0F, 0.0F, bladePresetZ[i][0]); //setRotationAngle(blade1, 0.0F, 0.0F, -0.5236F);
					blade[i].cubeList.add(new ModelBox(blade[i], 0, 2, -4.0F, -0.5F, -0.5F, 4, 1, 0, 0.0F, false));
					blade[i].cubeList.add(new ModelBox(blade[i], 0, 2, -8.0F, -0.5F, -0.5F, 4, 1, 0, 0.0F, false));
					blade[i].cubeList.add(new ModelBox(blade[i], 0, 2, -12.0F, -0.5F, -0.5F, 4, 1, 0, 0.0F, false));
					blade[i].cubeList.add(new ModelBox(blade[i], 0, 3, -16.0F, -0.5F, -0.5F, 4, 1, 0, 0.0F, false));
				}
				backBladesLeft = new ModelRenderer(this);
				backBladesLeft.setRotationPoint(3.2F, 6.0F, 3.0F);
				bipedBody.addChild(backBladesLeft);
				setRotationAngle(backBladesLeft, backBladesLeftPreset[0].x, backBladesLeftPreset[0].y, backBladesLeftPreset[0].z); //setRotationAngle(backBladesLeft, 0.0F, -0.5236F, 0.2618F);
				backBladesLeft.cubeList.add(new ModelBox(backBladesLeft, 0, 0, -0.2F, 0.0F, -1.0F, 2, 1, 1, 0.1F, true));
				backBladesLeft.cubeList.add(new ModelBox(backBladesLeft, 0, 0, 1.8F, 0.0F, -1.0F, 2, 1, 1, 0.1F, true));
				backBladesLeft.cubeList.add(new ModelBox(backBladesLeft, 0, 0, 3.8F, 0.0F, -1.0F, 2, 1, 1, 0.1F, true));
				backBladesLeft.cubeList.add(new ModelBox(backBladesLeft, 0, 0, 5.8F, 0.0F, -1.0F, 2, 1, 1, 0.1F, true));
				backBladesLeft.cubeList.add(new ModelBox(backBladesLeft, 0, 0, 7.8F, 0.0F, -1.0F, 2, 1, 1, 0.1F, true));
				bone12 = new ModelRenderer(this);
				bone12.setRotationPoint(8.8F, 0.5F, -0.5F);
				backBladesLeft.addChild(bone12);
				setRotationAngle(bone12, 0.0F, 0.7854F, 0.0F);
				for (int i = blade.length / 2; i < blade.length; i++) {
					blade[i] = new ModelRenderer(this);
					blade[i].setRotationPoint(0.0F, 0.0F, 0.5F);
					bone12.addChild(blade[i]);
					setRotationAngle(blade[i], 0.0F, 0.0F, bladePresetZ[i][0]); //setRotationAngle(blade[5], 0.0F, 0.0F, 0.5236F);
					blade[i].cubeList.add(new ModelBox(blade[i], 0, 2, 0.0F, -0.5F, -0.5F, 4, 1, 0, 0.0F, true));
					blade[i].cubeList.add(new ModelBox(blade[i], 0, 2, 4.0F, -0.5F, -0.5F, 4, 1, 0, 0.0F, true));
					blade[i].cubeList.add(new ModelBox(blade[i], 0, 2, 8.0F, -0.5F, -0.5F, 4, 1, 0, 0.0F, true));
					blade[i].cubeList.add(new ModelBox(blade[i], 0, 3, 12.0F, -0.5F, -0.5F, 4, 1, 0, 0.0F, true));
				}
				core = new ModelRenderer(this);
				core.setRotationPoint(0.0F, 5.0F, 0.9F);
				bipedBody.addChild(core);
				core.cubeList.add(new ModelBox(core, 14, 16, 0.75F, -3.6F, -3.0F, 3, 3, 0, 0.0F, false));
				bipedRightArm = new ModelRenderer(this);
				bipedRightArm.setRotationPoint(-5.0F, 2.5F, 0.0F);
				setRotationAngle(bipedRightArm, -0.1745F, 0.0F, 0.0F);
				bipedRightArm.cubeList.add(new ModelBox(bipedRightArm, 40, 16, -2.0F, -2.0F, -2.0F, 3, 12, 4, 0.0F, false));
				robeRightArm = new ModelRenderer(this);
				robeRightArm.setRotationPoint(0.0F, 0.0F, 0.0F);
				bipedRightArm.addChild(robeRightArm);
				robeRightArm.cubeList.add(new ModelBox(robeRightArm, 40, 32, -2.0F, -2.0F, -2.0F, 3, 12, 4, 0.5F, false));
				gunRight = new ModelRenderer(this);
				gunRight.setRotationPoint(0.0F, 0.0F, 0.0F);
				bipedRightArm.addChild(gunRight);
				gunRight.cubeList.add(new ModelBox(gunRight, 36, 16, -1.5F, 8.25F, -1.0F, 2, 2, 2, 0.0F, false));
				bipedLeftArm = new ModelRenderer(this);
				bipedLeftArm.setRotationPoint(5.0F, 2.5F, 0.0F);
				setRotationAngle(bipedLeftArm, 0.1745F, 0.0F, 0.0F);
				bipedLeftArm.cubeList.add(new ModelBox(bipedLeftArm, 40, 16, -1.0F, -2.0F, -2.0F, 3, 12, 4, 0.0F, true));
				robeLeftArm = new ModelRenderer(this);
				robeLeftArm.setRotationPoint(0.0F, 0.0F, 0.0F);
				bipedLeftArm.addChild(robeLeftArm);
				robeLeftArm.cubeList.add(new ModelBox(robeLeftArm, 40, 32, -1.0F, -2.0F, -2.0F, 3, 12, 4, 0.5F, true));
				gunLeft = new ModelRenderer(this);
				gunLeft.setRotationPoint(0.0F, 0.0F, 0.0F);
				bipedLeftArm.addChild(gunLeft);
				gunLeft.cubeList.add(new ModelBox(gunLeft, 36, 16, -0.5F, 8.25F, -1.0F, 2, 2, 2, 0.0F, true));
				bipedRightLeg = new ModelRenderer(this);
				bipedRightLeg.setRotationPoint(-1.9F, 12.0F, 0.0F);
				setRotationAngle(bipedRightLeg, 0.2618F, 0.0F, 0.0349F);
				bipedRightLeg.cubeList.add(new ModelBox(bipedRightLeg, 16, 48, -2.0F, 0.0F, -2.0F, 4, 12, 4, 0.0F, true));
				robeRightLeg = new ModelRenderer(this);
				robeRightLeg.setRotationPoint(0.0F, 0.0F, 0.0F);
				bipedRightLeg.addChild(robeRightLeg);
				robeRightLeg.cubeList.add(new ModelBox(robeRightLeg, 0, 32, -2.0F, 0.0F, -2.0F, 4, 12, 4, 0.5F, false));
				bipedLeftLeg = new ModelRenderer(this);
				bipedLeftLeg.setRotationPoint(1.9F, 12.0F, 0.0F);
				setRotationAngle(bipedLeftLeg, -0.2618F, 0.0F, -0.0349F);
				bipedLeftLeg.cubeList.add(new ModelBox(bipedLeftLeg, 16, 48, -2.0F, 0.0F, -2.0F, 4, 12, 4, 0.0F, false));
				robeLeftLeg = new ModelRenderer(this);
				robeLeftLeg.setRotationPoint(0.0F, 0.0F, 0.0F);
				bipedLeftLeg.addChild(robeLeftLeg);
				robeLeftLeg.cubeList.add(new ModelBox(robeLeftLeg, 0, 48, -2.0F, 0.0F, -2.0F, 4, 12, 4, 0.5F, false));
			}
	
			public void setRotationAngle(ModelRenderer modelRenderer, float x, float y, float z) {
				modelRenderer.rotateAngleX = x;
				modelRenderer.rotateAngleY = y;
				modelRenderer.rotateAngleZ = z;
			}

			@Override
			public void render(Entity entityIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scale) {
				if (entityIn instanceof EntityCustom) {
					boolean robeOff = ((EntityCustom)entityIn).isRobeOff();
					this.robeHead.showModel = !robeOff;
					this.robeBody.showModel = !robeOff;
					this.robeLeftArm.showModel = !robeOff;
					this.robeLeftLeg.showModel = !robeOff;
					this.robeRightArm.showModel = !robeOff;
					this.robeRightLeg.showModel = !robeOff;
					this.scrolls.showModel = robeOff;
					this.backBladesLeft.showModel = robeOff;
					this.backBladesRight.showModel = robeOff;
				}
				super.render(entityIn, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale);
			}

			@Override
			public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, float scaleFactor, Entity entityIn) {
				super.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor, entityIn);
				if (entityIn instanceof EntityCustom) {
					int robeOffTicks = ((EntityCustom)entityIn).getRobeOffTicks();
					if (robeOffTicks >= 0) {
						float partialTicks = ageInTicks - entityIn.ticksExisted;
						if (robeOffTicks <= ((EntityCustom)entityIn).bladesOpenTime) {
							float f = Math.min(((float)robeOffTicks + partialTicks) / ((EntityCustom)entityIn).bladesOpenTime, 1.0F);
							this.backBladesRight.rotateAngleX = this.backBladesRightPreset[0].x + (this.backBladesRightPreset[1].x - this.backBladesRightPreset[0].x) * f;
							this.backBladesRight.rotateAngleY = this.backBladesRightPreset[0].y + (this.backBladesRightPreset[1].y - this.backBladesRightPreset[0].y) * f;
							this.backBladesRight.rotateAngleZ = this.backBladesRightPreset[0].z + (this.backBladesRightPreset[1].z - this.backBladesRightPreset[0].z) * f;
							this.backBladesLeft.rotateAngleX = this.backBladesLeftPreset[0].x + (this.backBladesLeftPreset[1].x - this.backBladesLeftPreset[0].x) * f;
							this.backBladesLeft.rotateAngleY = this.backBladesLeftPreset[0].y + (this.backBladesLeftPreset[1].y - this.backBladesLeftPreset[0].y) * f;
							this.backBladesLeft.rotateAngleZ = this.backBladesLeftPreset[0].z + (this.backBladesLeftPreset[1].z - this.backBladesLeftPreset[0].z) * f;
							for (int i = 0; i < this.blade.length; i++) {
								this.blade[i].rotateAngleZ = this.bladePresetZ[i][0] + (this.bladePresetZ[i][1] - this.bladePresetZ[i][0]) * f;
							}
						} else {
							for (int i = 0; i < this.blade.length; i++) {
								this.blade[i].rotateAngleZ = this.bladePresetZ[i][1] + ageInTicks * 2.5132F;
							}
						}
					}
				}
			}
		}
		
		@SideOnly(Side.CLIENT)
		public class ModelSasoriCore extends ModelBase {
			private final ModelRenderer hexadecagon;
			private final ModelRenderer hexadecagon_r1;
			private final ModelRenderer hexadecagon_r2;
			private final ModelRenderer hexadecagon_r3;
			private final ModelRenderer hexadecagon_r4;
		
			public ModelSasoriCore() {
				textureWidth = 32;
				textureHeight = 32;
		
				hexadecagon = new ModelRenderer(this);
				hexadecagon.setRotationPoint(0.0F, 0.0F, 0.0F);
				hexadecagon.cubeList.add(new ModelBox(hexadecagon, 0, 15, -0.2984F, -3.0F, -2.0F, 0, 3, 4, 0.0F, false));
				hexadecagon.cubeList.add(new ModelBox(hexadecagon, 0, 5, -1.5F, -1.7984F, -2.0F, 3, 0, 4, 0.0F, false));
				hexadecagon.cubeList.add(new ModelBox(hexadecagon, 16, 0, -1.5F, -3.0F, -2.01F, 3, 3, 0, 0.0F, false));
		
				hexadecagon_r1 = new ModelRenderer(this);
				hexadecagon_r1.setRotationPoint(0.0F, -1.5F, 0.0F);
				hexadecagon.addChild(hexadecagon_r1);
				setRotationAngle(hexadecagon_r1, 0.0F, 0.0F, 0.3927F);
				hexadecagon_r1.cubeList.add(new ModelBox(hexadecagon_r1, 0, 0, -1.5F, -0.2984F, -2.0F, 3, 0, 4, 0.0F, false));
				hexadecagon_r1.cubeList.add(new ModelBox(hexadecagon_r1, 10, 11, -0.2984F, -1.5F, -2.0F, 0, 3, 4, 0.0F, false));
		
				hexadecagon_r2 = new ModelRenderer(this);
				hexadecagon_r2.setRotationPoint(0.0F, -1.5F, 0.0F);
				hexadecagon.addChild(hexadecagon_r2);
				setRotationAngle(hexadecagon_r2, 0.0F, 0.0F, -0.3927F);
				hexadecagon_r2.cubeList.add(new ModelBox(hexadecagon_r2, 0, 10, -1.5F, -0.2984F, -2.0F, 3, 0, 4, 0.0F, false));
				hexadecagon_r2.cubeList.add(new ModelBox(hexadecagon_r2, 16, 4, -0.2984F, -1.5F, -2.0F, 0, 3, 4, 0.0F, false));
		
				hexadecagon_r3 = new ModelRenderer(this);
				hexadecagon_r3.setRotationPoint(0.0F, -1.5F, 0.0F);
				hexadecagon.addChild(hexadecagon_r3);
				setRotationAngle(hexadecagon_r3, 0.0F, 0.0F, 0.7854F);
				hexadecagon_r3.cubeList.add(new ModelBox(hexadecagon_r3, 10, 1, -0.2984F, -1.5F, -2.0F, 0, 3, 4, 0.0F, false));
		
				hexadecagon_r4 = new ModelRenderer(this);
				hexadecagon_r4.setRotationPoint(0.0F, -1.5F, 0.0F);
				hexadecagon.addChild(hexadecagon_r4);
				setRotationAngle(hexadecagon_r4, 0.0F, 0.0F, -0.7854F);
				hexadecagon_r4.cubeList.add(new ModelBox(hexadecagon_r4, 16, 14, -0.2984F, -1.5F, -2.0F, 0, 3, 4, 0.0F, false));
			}
		
			@Override
			public void render(Entity entity, float f, float f1, float f2, float f3, float f4, float f5) {
				hexadecagon.render(f5);
			}
		
			public void setRotationAngle(ModelRenderer modelRenderer, float x, float y, float z) {
				modelRenderer.rotateAngleX = x;
				modelRenderer.rotateAngleY = y;
				modelRenderer.rotateAngleZ = z;
			}
		}
	}
}

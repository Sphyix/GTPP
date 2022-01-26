
package gtPlusPlus.xmod.gregtech.common.tileentities.machines.multi.production;

import codechicken.nei.ItemStackMap;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import gregtech.api.enums.Materials;
import gregtech.api.enums.TAE;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.*;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.items.GT_MetaGenerated_Tool;
import gregtech.api.metatileentity.implementations.*;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.*;
import gtPlusPlus.api.objects.data.AutoMap;
import gtPlusPlus.api.objects.data.Pair;
import gtPlusPlus.core.block.ModBlocks;
import gtPlusPlus.core.lib.CORE;
import gtPlusPlus.core.util.math.MathUtils;
import gtPlusPlus.core.util.minecraft.ItemUtils;
import gtPlusPlus.xmod.gregtech.api.metatileentity.implementations.base.GregtechMeta_MultiBlockBase;
import gtPlusPlus.xmod.gregtech.common.blocks.textures.TexturesGtBlock;
import gtPlusPlus.xmod.gregtech.common.helpers.TreeFarmHelper;
import gtPlusPlus.xmod.gregtech.common.helpers.treefarm.TreeGenerator;
import joptsimple.util.KeyValuePair;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import javax.xml.crypto.dsig.keyinfo.KeyValue;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.*;
import static gregtech.api.util.GT_StructureUtility.ofHatchAdder;

public class GregtechMetaTileEntityTreeFarm extends GregtechMeta_MultiBlockBase<GregtechMetaTileEntityTreeFarm> {

	public static int CASING_TEXTURE_ID;
	public static String mCasingName = "Sterile Farm Casing";
	public static TreeGenerator mTreeData;
	private int mCasing;
	private IStructureDefinition<GregtechMetaTileEntityTreeFarm> STRUCTURE_DEFINITION = null;

	private ItemStack currSapling;
	private int currSlot = 0;
	private GT_MetaTileEntity_Hatch_InputBus currInputBus;
	private ItemStack mTreeType;

	private HashMap<String, ItemStack> sLogCache = new HashMap<String, ItemStack>();

	static {
		new Thread("GTPP-TreeDataWorker") {
			@Override
			public void run() {
				mTreeData = new TreeGenerator();
			}
		}.start();
	}

	private static ItemStack aLeaves;

	public GregtechMetaTileEntityTreeFarm(final int aID, final String aName, final String aNameRegional) {
		super(aID, aName, aNameRegional);
		CASING_TEXTURE_ID = TAE.getIndexFromPage(1, 15);
	}

	public GregtechMetaTileEntityTreeFarm(final String aName) {
		super(aName);
		CASING_TEXTURE_ID = TAE.getIndexFromPage(1, 15);
	}

	public IMetaTileEntity newMetaEntity(final IGregTechTileEntity aTileEntity) {
		return new GregtechMetaTileEntityTreeFarm(this.mName);
	}

	@Override
	public String getMachineType() {
		return "Tree Farm";
	}

	@Override
	protected GT_Multiblock_Tooltip_Builder createTooltip() {
		GT_Multiblock_Tooltip_Builder tt = new GT_Multiblock_Tooltip_Builder();
		tt.addMachineType(getMachineType())
				.addInfo("Converts EU to Oak Logs")
				.addInfo("Eu Usage: 100% | Parallel: 1")
				.addInfo("Requires a Saw or Chainsaw in GUI slot")
				.addPollutionAmount(getPollutionPerSecond(null))
				.addSeparator()
				.beginStructureBlock(3, 3, 3, true)
				.addController("Front center")
				.addCasingInfo("Sterile Farm Casing", 10)
				.addInputBus("Any casing", 1)
				.addOutputBus("Any casing", 1)
				.addEnergyHatch("Any casing", 1)
				.addMaintenanceHatch("Any casing", 1)
				.addMufflerHatch("Any casing", 1)
				.toolTipFinisher(CORE.GT_Tooltip_Builder);
		return tt;
	}

	public ITexture[] getTexture(final IGregTechTileEntity aBaseMetaTileEntity, final byte aSide, final byte aFacing,
								 final byte aColorIndex, final boolean aActive, final boolean aRedstone) {
		if (aSide == aFacing) {
			return new ITexture[]{Textures.BlockIcons.getCasingTextureForId(CASING_TEXTURE_ID),
					TextureFactory.of(aActive ? TexturesGtBlock.Overlay_Machine_Controller_Advanced_Active : TexturesGtBlock.Overlay_Machine_Controller_Advanced)};
		}
		return new ITexture[]{Textures.BlockIcons.getCasingTextureForId(CASING_TEXTURE_ID)};
	}

	@Override
	public boolean hasSlotInGUI() {
		return true;
	}

	@Override
	public boolean requiresVanillaGtGUI() {
		return true;
	}

	@Override
	public String getCustomGUIResourceName() {
		return "VacuumFreezer";
	}

	public GT_Recipe.GT_Recipe_Map getRecipeMap() {
		return null;
	}

	public boolean isCorrectMachinePart(final ItemStack aStack) {
		// is correct part && either not powered tool or have enough power
		return TreeFarmHelper.getPartType(aStack) != null && !GT_ModHandler.isElectricItem(aStack) || GT_ModHandler.canUseElectricItem(aStack, 1);
	}

	public boolean checkRecipe(final ItemStack aStack) {
		if (mTreeData == null)
			// not finished somehow
			return false;
		if (aStack == null && !replaceTool())
			// no tool
			return false;
		if (!isCorrectMachinePart(aStack))
			// not a tool
			return false;
		if (mTreeData != null) {

			long tVoltage = getMaxInputVoltage();
			byte tTier = (byte) Math.max(1, GT_Utility.getTier(tVoltage));

			this.mMaxProgresstime = 100;
			this.mEUt = (int) tVoltage;

			this.mEfficiency = (10000 - (getIdealStatus() - getRepairStatus()) * 1000);
			this.mEfficiencyIncrease = 10000;

			// Overclock
			if (this.mEUt <= 16) {
				this.mEUt = (this.mEUt * (1 << tTier - 1) * (1 << tTier - 1));
				this.mMaxProgresstime = (this.mMaxProgresstime / (1 << tTier - 1));
			} else {
				while (this.mEUt <= gregtech.api.enums.GT_Values.V[(tTier - 1)]) {
					this.mEUt *= 4;
					this.mMaxProgresstime /= 2;
				}
			}

			if (this.mEUt > 0) {
				this.mEUt = (-this.mEUt);
			}


			int aChance = MathUtils.randInt(0, 10);

			try {
				if (aChance < 8) {
					ItemStackMap<Integer> allOutputs = new ItemStackMap<>();
					if (aLeaves == null)
						aLeaves = ItemUtils.getSimpleStack(Blocks.leaves);
					//1% Chance per Tick				
					for (int u = 0; u < (Math.max(4, (MathUtils.randInt((3 * tTier), 100) * tTier * tTier) / 14)); u++) {
						AutoMap<ItemStack> aOutputs = mTreeData.generateOutput(0);
						if (aOutputs.size() > 0) {
							for (ItemStack aOutputItemStack : aOutputs) {
								if (!GT_Utility.areStacksEqual(aLeaves, aOutputItemStack)) {
									Integer oldStackSize = allOutputs.get(aOutputItemStack);
									int oldStackSizeUnboxed = oldStackSize == null ? 0 : oldStackSize;
									allOutputs.put(aOutputItemStack, oldStackSizeUnboxed + aOutputItemStack.stackSize);
								}
							}
						}
					}

					mOutputItems = allOutputs.entries().stream()
							.map(e -> {
								e.key.stackSize = e.value;
								return e.key;
							}).toArray(ItemStack[]::new);
				}
			} catch (Throwable t) {
				t.printStackTrace(GT_Log.err);
			}
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean checkHatch() {
		return super.checkHatch() && mEnergyHatches.size() == 1;
	}

	@Override
	public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
		mCasing = 0;
		return checkPiece(mName, 1, 1, 0) && mCasing >= 10 - 8 && checkHatch();
	}

	@Override
	public int getMaxParallelRecipes() {
		return 1;
	}

	@Override
	public int getEuDiscountForParallelism() {
		return 0;
	}

	@Override
	public IStructureDefinition<GregtechMetaTileEntityTreeFarm> getStructureDefinition() {
		if (STRUCTURE_DEFINITION == null) {
			STRUCTURE_DEFINITION = StructureDefinition.<GregtechMetaTileEntityTreeFarm>builder()
					.addShape(mName, transpose(new String[][]{
							{"CCC", "CCC", "CCC"},
							{"C~C", "C-C", "CCC"},
							{"CCC", "CCC", "CCC"},
					}))
					.addElement(
							'C',
							ofChain(
									ofHatchAdder(
											GregtechMetaTileEntityTreeFarm::addTreeFarmList, CASING_TEXTURE_ID, 1
									),
									onElementPass(
											x -> ++x.mCasing,
											ofBlock(
													ModBlocks.blockCasings2Misc, 15
											)
									)
							)
					)
					.build();
		}
		return STRUCTURE_DEFINITION;
	}

	public final boolean addTreeFarmList(IGregTechTileEntity aTileEntity, int aBaseCasingIndex) {
		if (aTileEntity == null) {
			return false;
		} else {
			return addToMachineList(aTileEntity, aBaseCasingIndex);
		}
	}

	public int getMaxEfficiency(final ItemStack aStack) {
		return 10000;
	}

	public int getPollutionPerSecond(final ItemStack aStack) {
		return CORE.ConfigSwitches.pollutionPerSecondMultiTreeFarm;
	}

	public int getDamageToComponent(final ItemStack aStack) {
		return MathUtils.balance((int) (75 - ((GT_MetaGenerated_Tool.getPrimaryMaterial(aStack).getMass()))), 5, 120);
	}

	public boolean explodesOnComponentBreak(final ItemStack aStack) {
		return false;
	}

	public boolean replaceTool() {
		ItemStack invItem = this.mInventory[1];
		if (invItem == null) {
			for (GT_MetaTileEntity_Hatch_InputBus mInputBus : this.mInputBusses) {
				for (int i = 0; i < mInputBus.mInventory.length; i++) {
					ItemStack uStack = mInputBus.mInventory[i];
					if (uStack != null && TreeFarmHelper.getPartType(uStack) != null) {
						this.setGUIItemStack(uStack);
						return true;
					}
				}
			}
		}
		return false;
	}

	public void getWoodFromSapling() {
		if(sLogCache.size() == 0)
			loadMapWoodFromSapling();

		if(this.currSapling != null && this.currInputBus != null){
			ItemStack uStack = this.currInputBus.mInventory[this.currSlot];
			if(uStack == this.currSapling)
				return;
		}
		for (GT_MetaTileEntity_Hatch_InputBus mInputBus : this.mInputBusses) {
			for (int i = 0; i < mInputBus.mInventory.length; i++) {
				ItemStack uStack = mInputBus.mInventory[i];
				if(uStack != null) {
					String registryName = Item.itemRegistry.getNameForObject(uStack.getItem());
					ItemStack aWood = sLogCache.get(registryName + ":" + uStack.getItemDamage());
					if (aWood != null) {
						this.currSapling = uStack;
						this.currInputBus = mInputBus;
						this.currSlot = i;
						this.mTreeType = aWood;
						return;
					}
				}
			}
			this.mTreeType = new ItemStack(Blocks.log, 1,0); //default to oak wood
		}
	}

	public void loadMapWoodFromSapling(){

		//minecraft
		sLogCache.put("minecraft:sapling:0", new ItemStack(Blocks.log,1, 0)); //oak
		sLogCache.put("minecraft:sapling:1", new ItemStack(Blocks.log,1, 1)); //spruce
		sLogCache.put("minecraft:sapling:2", new ItemStack(Blocks.log,1, 2)); //birch
		sLogCache.put("minecraft:sapling:3", new ItemStack(Blocks.log,1, 3)); //jungle
		sLogCache.put("minecraft:sapling:4", new ItemStack(Blocks.log2,1, 0)); //acacia
		sLogCache.put("minecraft:sapling:5", new ItemStack(Blocks.log2,1, 1)); //dark oak

		//galaxySpace
		sLogCache.put("GalaxySpace:barnardaCsapling", GT_ModHandler.getModItem("GalaxySpace","barnardaClog", 1)); //barnarda c

		//ic2
		sLogCache.put("IC2:blockRubSapling", GT_ModHandler.getModItem("IC2","blockRubWood", 1)); //rubber
//
//		//natura
//		sLogCache.put(GT_ModHandler.getModItem("Natura","florasapling", 1, 1), GT_ModHandler.getModItem("Natura","tree", 1, 0)); //eucalyptus
//		sLogCache.put(GT_ModHandler.getModItem("Natura","florasapling", 1, 2), GT_ModHandler.getModItem("Natura","tree", 1, 3)); //hopseed
//		sLogCache.put(GT_ModHandler.getModItem("Natura","florasapling", 1, 3), GT_ModHandler.getModItem("Natura","tree", 1, 1)); //sakura
//		sLogCache.put(GT_ModHandler.getModItem("Natura","florasapling", 1, 4), GT_ModHandler.getModItem("Natura","tree", 1, 2)); //ghostwood
//		sLogCache.put(GT_ModHandler.getModItem("Natura","florasapling", 1, 5), GT_ModHandler.getModItem("Natura","bloodwood", 1, 0)); //bloodwood
//		sLogCache.put(GT_ModHandler.getModItem("Natura","florasapling", 1, 6), GT_ModHandler.getModItem("Natura","Dark Tree", 1, 0)); //darkwood
//		sLogCache.put(GT_ModHandler.getModItem("Natura","florasapling", 1, 7), GT_ModHandler.getModItem("Natura","Dark Tree", 1, 1)); //fusewood
//
//		sLogCache.put(GT_ModHandler.getModItem("Natura","Rare Sapling", 1, 0), GT_ModHandler.getModItem("Natura","Rare Tree", 1, 0)); //maple
//		sLogCache.put(GT_ModHandler.getModItem("Natura","Rare Sapling", 1, 1), GT_ModHandler.getModItem("Natura","Rare Tree", 1, 1)); //silverbell
//		sLogCache.put(GT_ModHandler.getModItem("Natura","Rare Sapling", 1, 2), GT_ModHandler.getModItem("Natura","Rare Tree", 1, 2)); //amaranth
//		sLogCache.put(GT_ModHandler.getModItem("Natura","Rare Sapling", 1, 3), GT_ModHandler.getModItem("Natura","Rare Tree", 1, 3)); //tigerwood
//		sLogCache.put(GT_ModHandler.getModItem("Natura","Rare Sapling", 1, 4), GT_ModHandler.getModItem("Natura","willow", 1, 0)); //willow
//
//		//TConstruct
//		sLogCache.put(GT_ModHandler.getModItem("TConstruct","slime.sapling", 1), GT_ModHandler.getModItem("TConstruct","slime.gel", 1)); //green slime blocks
//
//		//TaintedMagic
//		sLogCache.put(GT_ModHandler.getModItem("TaintedMagic","BlockWarpwoodSapling", 1), GT_ModHandler.getModItem("TaintedMagic","BlockWarpwoodLog", 1)); //warpwood
//
//		//Thaumcraft
//		sLogCache.put(GT_ModHandler.getModItem("Thaumcraft","blockCustomPlant", 1, 0), GT_ModHandler.getModItem("Thaumcraft","blockMagicalLog", 1, 0)); //greatwood
//		sLogCache.put(GT_ModHandler.getModItem("Thaumcraft","blockCustomPlant", 1, 1), GT_ModHandler.getModItem("Thaumcraft","blockMagicalLog", 1, 1)); //silverwood
//
//		//gt++
//		sLogCache.put(GT_ModHandler.getModItem("miscutils","blockRainforestOakSapling", 1), GT_ModHandler.getModItem("miscutils","blockRainforestOakLog", 1)); //gt++ rainforest
//		sLogCache.put(GT_ModHandler.getModItem("miscutils","blockPineSapling", 1), GT_ModHandler.getModItem("miscutils","blockPineLogLog", 1)); //gt++ pine
	}

	public boolean tryDamageTool(ItemStack invItem) {
		if (invItem != null && invItem.getItem() instanceof GT_MetaGenerated_Tool) {
			long aDmg = GT_MetaGenerated_Tool.getToolDamage(invItem);
			long aDmgMax = GT_MetaGenerated_Tool.getToolMaxDamage(invItem);
			if (aDmg < aDmgMax && GT_MetaGenerated_Tool.getPrimaryMaterial(invItem) != Materials._NULL) {
				return GT_ModHandler.damageOrDechargeItem(invItem, 1, 0, null);
			}
		}
		return false;
	}

	@Override
	public boolean doRandomMaintenanceDamage() {
		ItemStack tSaw = mInventory[1];
		if (!isCorrectMachinePart(tSaw) || getRepairStatus() == 0) {
			stopMachine();
			return false;
		}
		if (CORE.RANDOM.nextInt(200) == 0) {
			if (!tryDamageTool(tSaw)) {
				if (tSaw.getItem().isDamageable())
					addOutput(tSaw);
				this.mInventory[1] = null;
				if (!replaceTool())
					this.getBaseMetaTileEntity().disableWorking();
				tryDamageTool(tSaw);
			}
		}
		return super.doRandomMaintenanceDamage();
	}

	@Override
	public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
		super.onPostTick(aBaseMetaTileEntity, aTick);
		replaceTool();
	}

	@Override
	public void construct(ItemStack stackSize, boolean hintsOnly) {

		buildPiece(mName , stackSize, hintsOnly, 1, 1, 0);
	}
}

package net.minecraft.src.buildcraft.factory;

import net.minecraft.src.Block;
import net.minecraft.src.BuildCraftBlockUtil;
import net.minecraft.src.BuildCraftCore;
import net.minecraft.src.BuildCraftFactory;
import net.minecraft.src.EntityItem;
import net.minecraft.src.ItemStack;
import net.minecraft.src.NBTTagCompound;
import net.minecraft.src.Packet;
import net.minecraft.src.Packet230ModLoader;
import net.minecraft.src.mod_BuildCraftFactory;
import net.minecraft.src.buildcraft.api.APIProxy;
import net.minecraft.src.buildcraft.api.IAreaProvider;
import net.minecraft.src.buildcraft.api.LaserKind;
import net.minecraft.src.buildcraft.api.Orientations;
import net.minecraft.src.buildcraft.core.BlockContents;
import net.minecraft.src.buildcraft.core.BlockIndex;
import net.minecraft.src.buildcraft.core.BluePrint;
import net.minecraft.src.buildcraft.core.BluePrintBuilder;
import net.minecraft.src.buildcraft.core.CoreProxy;
import net.minecraft.src.buildcraft.core.DefaultAreaProvider;
import net.minecraft.src.buildcraft.core.EntityBlock;
import net.minecraft.src.buildcraft.core.IMachine;
import net.minecraft.src.buildcraft.core.StackUtil;
import net.minecraft.src.buildcraft.core.TileCurrentPowered;
import net.minecraft.src.buildcraft.core.Utils;

public class TileQuarry extends TileCurrentPowered implements IArmListener, IMachine {		
	boolean isDigging = false;
	
	boolean inProcess = false;
	
	EntityMechanicalArm arm;
	
	private int xMin = -1, zMin = -1;
	private int xSize = -1, ySize = -1, zSize = -1;
	
	boolean loadArm = false;
	
	int targetX, targetY, targetZ;
	EntityBlock [] lasers;
	
	BluePrintBuilder bluePrintBuilder;
	
	public TileQuarry() {
		latency = 20;
	}
	
    public void createUtilsIfNeeded () {
    	if (bluePrintBuilder == null) {
    		if (xSize == -1) {
    			setBoundaries(loadDefaultBoundaries);
    		}
    		    
    		initializeBluePrintBuilder();
    	}    	
    	
		bluePrintBuilder.findNextBlock(worldObj);
    	
    	if (bluePrintBuilder.done) {    	
    		deleteLasers ();
    		
    		if (arm == null) {
    			createArm ();
    		}

    		if (loadArm) {
    			arm.joinToWorld(worldObj);
    			loadArm = false;
    			
    			if (findTarget(false)) {    				
    	    		isDigging = true;
    	    	}
    		}
    	} else {    		
    		createLasers();    		
    		isDigging = true;
    	}
    }
	
	private boolean loadDefaultBoundaries = false;
	
	private void createArm () {
		arm = new EntityMechanicalArm(worldObj, xMin + Utils.pipeMaxSize,
				yCoord + bluePrintBuilder.bluePrint.sizeY - 1
						+ Utils.pipeMinSize, zMin + Utils.pipeMaxSize,
				bluePrintBuilder.bluePrint.sizeX - 2 + Utils.pipeMinSize * 2,
				bluePrintBuilder.bluePrint.sizeZ - 2 + Utils.pipeMinSize * 2);

		arm.listener = this;
		loadArm = true;
	}
	
	private void createLasers () {
		if (!APIProxy.isServerSide()) {
			if (lasers == null) {				
				lasers = Utils.createLaserBox(worldObj, xMin, yCoord, zMin,
						xMin + xSize - 1, yCoord + ySize - 1, zMin + zSize - 1,
						LaserKind.Stripes);
			}
		}
	}
	
	private void deleteLasers () {
		if (lasers != null) {
			for (EntityBlock l : lasers) {
				APIProxy.removeEntity(l);
			}
			
			lasers = null;
		}
	}
	
	public void doWork() {
		if (inProcess) {
			return;
		}
		
		if (!isDigging) {
			return;
		}	
		
	    createUtilsIfNeeded();
	    
	    if (bluePrintBuilder == null) {
	    	return;
	    }
	    
    	if (bluePrintBuilder.done && bluePrintBuilder.findNextBlock(worldObj) != null) {
    		// In this case, the Quarry has been broken. Repair it.
    		bluePrintBuilder.done = false;
    		
    		createLasers();
    	}
	    
		if (!bluePrintBuilder.done) {
			lastWorkTime = worldObj.getWorldTime();
			BlockContents contents = bluePrintBuilder.findNextBlock(worldObj);
			
			if (contents != null) {		
				int blockId = worldObj.getBlockId(contents.x, contents.y, contents.z);
				
				worldObj.setBlockWithNotify(contents.x, contents.y, contents.z,
						contents.blockId);
				
				if (blockId != 0) {
					Block.blocksList[blockId].dropBlockAsItem(
							worldObj,
							contents.x, contents.y, contents.z, blockId);
				}				
			}
			
			return;
		} 	  					
		
		if (!findTarget(true)) {
			arm.setTarget (xMin + arm.sizeX / 2, yCoord + 2, zMin + arm.sizeX / 2);
						
			isDigging = false;			
		}
		
		inProcess = true;
		
		if (APIProxy.isServerSide()) {
			CoreProxy.sendToPlayers(getUpdatePacket(), xCoord, yCoord, zCoord,
					50, mod_BuildCraftFactory.instance);
		}
	}

	public boolean findTarget (boolean doSet) {
		boolean[][] blockedColumns = new boolean[bluePrintBuilder.bluePrint.sizeX - 2][bluePrintBuilder.bluePrint.sizeZ - 2];
		
		for (int searchX = 0; searchX < bluePrintBuilder.bluePrint.sizeX - 2; ++searchX) {
			for (int searchZ = 0; searchZ < bluePrintBuilder.bluePrint.sizeZ - 2; ++searchZ) {
				blockedColumns [searchX][searchZ] = false;
			}
		}
		
		for (int searchY = yCoord + 3; searchY >= 0; --searchY) {
			int startX, endX, incX;
			
			if (searchY % 2 == 0) {
				startX = 0;
				endX = bluePrintBuilder.bluePrint.sizeX - 2;
				incX = 1;
			} else {
				startX = bluePrintBuilder.bluePrint.sizeX - 3;
				endX = -1;
				incX = -1;
			}
			
			for (int searchX = startX; searchX != endX; searchX += incX) {
				int startZ, endZ, incZ;
				
				if (searchX % 2 == searchY % 2) {
					startZ = 0;
					endZ = bluePrintBuilder.bluePrint.sizeZ - 2;
					incZ = 1;
				} else {
					startZ = bluePrintBuilder.bluePrint.sizeZ - 3;
					endZ = -1;
					incZ = -1;
				}
								
				for (int searchZ = startZ; searchZ != endZ; searchZ += incZ) {
					if (!blockedColumns [searchX][searchZ]) {
						int bx = xMin + searchX + 1, by = searchY, bz = zMin + searchZ + 1;
						
						int blockId = worldObj.getBlockId(bx, by, bz);
						
						if (blockDig (blockId)) {		
							blockedColumns [searchX][searchZ] = true;						
						} else if (canDig(blockId)) {
							if (doSet) {
								arm.setTarget (bx, by + 1, bz);

								targetX = bx;
								targetY = by;
								targetZ = bz;
							}
							
							return true;
						}
					}
				}
			}
		}

		return false;
	}
	
	public void readFromNBT(NBTTagCompound nbttagcompound) {
		super.readFromNBT(nbttagcompound);		

		if (nbttagcompound.hasKey("xSize")) {
			xMin = nbttagcompound.getInteger("xMin");
			zMin = nbttagcompound.getInteger("zMin");

			xSize = nbttagcompound.getInteger("xSize");
			ySize = nbttagcompound.getInteger("ySize");
			zSize = nbttagcompound.getInteger("zSize");
			
			loadDefaultBoundaries = false;
		} else {
			// This is a legacy save, compute boundaries
			
			loadDefaultBoundaries = true;
		}				
		
		targetX = nbttagcompound.getInteger("targetX");
		targetY = nbttagcompound.getInteger("targetY");
		targetZ = nbttagcompound.getInteger("targetZ");
		
		if (nbttagcompound.getBoolean("hasArm")) {
			NBTTagCompound armStore = nbttagcompound.getCompoundTag("arm");
			arm = new EntityMechanicalArm(worldObj);
			arm.readFromNBT(armStore);
			arm.listener = this;

			loadArm = true;
		}
	}

	public void writeToNBT(NBTTagCompound nbttagcompound) {
		super.writeToNBT(nbttagcompound);		
		
		nbttagcompound.setInteger("xMin", xMin);
		nbttagcompound.setInteger("zMin", zMin);
		
		nbttagcompound.setInteger("xSize", xSize);
		nbttagcompound.setInteger("ySize", ySize);
		nbttagcompound.setInteger("zSize", zSize);
		
		nbttagcompound.setInteger("targetX", targetX);
		nbttagcompound.setInteger("targetY", targetY);
		nbttagcompound.setInteger("targetZ", targetZ);
		nbttagcompound.setBoolean("hasArm", arm != null);
		
		if (arm != null) {
			NBTTagCompound armStore = new NBTTagCompound();
			nbttagcompound.setTag("arm", armStore);
			arm.writeToNBT(armStore);
		}
	}
	
	
	@Override
	public void positionReached(EntityMechanicalArm arm) {
		inProcess = false;
		
		if (APIProxy.isClient(worldObj)) {
			return;
		}
		
		int i = targetX;
		int j = targetY;
		int k = targetZ;				
		
		int blockId = worldObj.getBlockId((int) i, (int) j, (int) k);
		
		if (canDig(blockId)) {
			lastWorkTime = worldObj.getWorldTime();
			
			// Share this with mining well!			
			
			ItemStack stack = BuildCraftBlockUtil.getItemStackFromBlock(
					worldObj, i, j, k);

			if (stack != null) {
				boolean added = false;

				// First, try to add to a nearby chest

				StackUtil stackUtils = new StackUtil(stack);
				
				added = stackUtils.addToRandomInventory(this,
						Orientations.Unknown);

				if (!added) {
					added = Utils.addToRandomPipeEntry(this,
							Orientations.Unknown, stack);
				}

				// Last, throw the object away

				if (!added) {
					float f = worldObj.rand.nextFloat() * 0.8F + 0.1F;
					float f1 = worldObj.rand.nextFloat() * 0.8F + 0.1F;
					float f2 = worldObj.rand.nextFloat() * 0.8F + 0.1F;

					EntityItem entityitem = new EntityItem(worldObj,
							(float) xCoord + f, (float) yCoord + f1 + 0.5F,
							(float) zCoord + f2, stack);

					float f3 = 0.05F;
					entityitem.motionX = (float) worldObj.rand
					.nextGaussian() * f3;
					entityitem.motionY = (float) worldObj.rand
					.nextGaussian() * f3 + 1.0F;
					entityitem.motionZ = (float) worldObj.rand
					.nextGaussian() * f3;
					worldObj.entityJoinedWorld(entityitem);
				}				
			}
					
			worldObj.setBlockWithNotify((int) i, (int) j, (int) k, 0);
		}		
	}
	
	boolean blockDig (int blockID) {
		return blockID == Block.bedrock.blockID
				|| blockID == Block.lavaStill.blockID
				|| blockID == Block.lavaMoving.blockID;
	}
	
	boolean canDig(int blockID) {
		return !blockDig(blockID) && blockID != 0
				&& blockID != Block.waterMoving.blockID
				&& blockID != Block.waterStill.blockID
				&& blockID != Block.snow.blockID
				&& Block.blocksList [blockID] != null;
	}
	
	public void delete () {
		if (arm != null) {
			arm.setEntityDead ();
		}
		
		deleteLasers();
	}

	@Override
	public boolean isActive() {
		return isDigging;
	}
	
	private void setBoundaries (boolean useDefault) {
		IAreaProvider a = null;
		
		if (!useDefault) {
			a = Utils.getNearbyAreaProvider(worldObj, xCoord, yCoord,
				zCoord);
		}
		
		if (a == null) {
			a = new DefaultAreaProvider (1, 1, 1, 11, 5, 11);
			
			useDefault = true;
		}
		
		xSize = a.xMax() - a.xMin() + 1;
		ySize = a.yMax() - a.yMin() + 1;
		zSize = a.zMax() - a.zMin() + 1;
		
		if (xSize < 3 || zSize < 3) {
			a = new DefaultAreaProvider (1, 1, 1, 11, 5, 11);
			
			useDefault = true;
		}
		
		xSize = a.xMax() - a.xMin() + 1;
		ySize = a.yMax() - a.yMin() + 1;
		zSize = a.zMax() - a.zMin() + 1;
		
		if (ySize < 5) {
			ySize = 5;
		}
		
		if (useDefault) {
			Orientations o = Orientations.values()[worldObj.getBlockMetadata(
					xCoord, yCoord, zCoord)].reverse();

			switch (o) {
			case XPos:
				xMin = xCoord + 1;
				zMin = zCoord - 4 - 1;
				break;
			case XNeg:
				xMin = xCoord - 9 - 2;
				zMin = zCoord - 4 - 1;
				break;
			case ZPos:
				xMin = xCoord - 4 - 1;
				zMin = zCoord + 1;
				break;
			case ZNeg:
				xMin = xCoord - 4 - 1;
				zMin = zCoord - 9 - 2;
				break;
			}
		} else {
			xMin = a.xMin();
			zMin = a.zMin();
		}
		
		a.removeFromWorld();
	}
	
	private void initializeBluePrintBuilder () {
		BluePrint bluePrint = new BluePrint(xSize, ySize, zSize);	
	
		for (int i = 0; i < bluePrint.sizeX; ++i) {
			for (int j = 0; j < bluePrint.sizeY; ++j) {
				for (int k = 0; k < bluePrint.sizeZ; ++k) {
					bluePrint.setBlockId(i, j, k, 0);
				}
			}
		}

		for (int it = 0; it < 2; it++) {
			for (int i = 0; i < bluePrint.sizeX; ++i) {
				bluePrint.setBlockId(i, it * (ySize - 1), 0,
						BuildCraftFactory.frameBlock.blockID);
				bluePrint.setBlockId(i, it * (ySize - 1), bluePrint.sizeZ - 1,
						BuildCraftFactory.frameBlock.blockID);
			}

			for (int k = 0; k < bluePrint.sizeZ; ++k) {
				bluePrint.setBlockId(0, it * (ySize - 1), k,
						BuildCraftFactory.frameBlock.blockID);
				bluePrint.setBlockId(bluePrint.sizeX - 1, it * (ySize - 1), k,
						BuildCraftFactory.frameBlock.blockID);

			}
		}

		for (int h = 1; h < ySize; ++h) {
			bluePrint.setBlockId(0, h, 0,
					BuildCraftFactory.frameBlock.blockID);
			bluePrint.setBlockId(0, h, bluePrint.sizeZ - 1,
					BuildCraftFactory.frameBlock.blockID);
			bluePrint.setBlockId(bluePrint.sizeX - 1, h, 0,
					BuildCraftFactory.frameBlock.blockID);
			bluePrint.setBlockId(bluePrint.sizeX - 1, h,
					bluePrint.sizeZ - 1,
					BuildCraftFactory.frameBlock.blockID);
		}
		
		bluePrintBuilder = new BluePrintBuilder(bluePrint, xMin, yCoord, zMin);		
	}
	
	public Packet getDescriptionPacket() {
		Packet230ModLoader packet = new Packet230ModLoader();

		packet.modId = mod_BuildCraftFactory.instance.getId();
		packet.packetType = BuildCraftFactory.tileQuarryDescriptionPacket;

		packet.dataInt = new int [8];
		packet.dataInt [0] = xCoord;
		packet.dataInt [1] = yCoord;
		packet.dataInt [2] = zCoord;
		packet.dataInt [3] = xMin;
		packet.dataInt [4] = zMin;
		packet.dataInt [5] = xSize;
		packet.dataInt [6] = ySize;
		packet.dataInt [7] = zSize;

		packet.dataFloat = new float [3];
		
		if (arm != null) {
			double [] headPos = arm.getHeadPosition();
			
			packet.dataFloat [0] = (float) headPos [0];
			packet.dataFloat [1] = (float) headPos [1];
			packet.dataFloat [2] = (float) headPos [2];
		} else {
			packet.dataFloat [0] = 0;
			packet.dataFloat [1] = 0;
			packet.dataFloat [2] = 0;
		}
		
		return packet;
    }
	
	public Packet230ModLoader getUpdatePacket() {
		Packet230ModLoader packet = new Packet230ModLoader();
		
		packet.modId = mod_BuildCraftFactory.instance.getId();
		packet.packetType = BuildCraftFactory.tileQuarryUpdatePacket;
		
		packet.dataInt = new int [3];
		packet.dataInt [0] = xCoord;
		packet.dataInt [1] = yCoord;
		packet.dataInt [2] = zCoord;
		
		packet.dataFloat = new float [6];
		
		if (arm != null) {
			double [] headPos = arm.getHeadPosition();
			double [] target = arm.getTarget();
			
			packet.dataFloat [0] = (float) headPos [0];
			packet.dataFloat [1] = (float) headPos [1];
			packet.dataFloat [2] = (float) headPos [2];
			
			packet.dataFloat [3] = (float) target [0];
			packet.dataFloat [4] = (float) target [1];
			packet.dataFloat [5] = (float) target [2];
		} else {
			packet.dataFloat [0] = 0;
			packet.dataFloat [1] = 0;
			packet.dataFloat [2] = 0;
			
			packet.dataFloat [3] = 0;
			packet.dataFloat [4] = 0;
			packet.dataFloat [5] = 0;

		}

		return packet;
    }
	
	public void handleUpdatePacket (Packet230ModLoader packet) {
		if (packet.packetType != BuildCraftFactory.tileQuarryUpdatePacket) {
			return;
		}
		
		if (packet.dataFloat[0] == 0 && packet.dataFloat[1] == 0
				&& packet.dataFloat[2] == 0) {
			return;
		}
		
		createUtilsIfNeeded();
		
		if (arm != null) {
			arm.setHeadPosition(packet.dataFloat[0], packet.dataFloat[1],
					packet.dataFloat[2]);
			
			arm.setTarget(packet.dataFloat[3], packet.dataFloat[4],
					packet.dataFloat[5]);
		}
	}
	
	public void handleDescriptionPacket (Packet230ModLoader packet) {
		if (packet.packetType != BuildCraftFactory.tileQuarryDescriptionPacket) {
			return;
		}
		
		int xMin = packet.dataInt [3];
		int zMin = packet.dataInt [4];
		int xSize = packet.dataInt [5];
		int ySize = packet.dataInt [6];
		int zSize = packet.dataInt [7];
		
		if (init && 
				(xMin != this.xMin
				|| zMin != this.zMin
				|| xSize != this.xSize
				|| ySize != this.ySize
				|| zSize != this.zSize)) {
			init = false;
			
			this.xMin = xMin;
			this.zMin = zMin;
			this.xSize = xSize;
			this.ySize = ySize;
			this.zSize = zSize;
			
			deleteLasers();				
			bluePrintBuilder = null;
			
			createUtilsIfNeeded();
			
			if (arm != null) {			
				arm.setHeadPosition(packet.dataFloat[0], packet.dataFloat[1],
						packet.dataFloat[2]);
			}
		}
	}
	
	public void initialize () {
		super.initialize();
		
		createUtilsIfNeeded ();
		
		BlockIndex index = new BlockIndex(xCoord, yCoord, zCoord);
		
		if (BuildCraftCore.bufferedDescriptions.containsKey(index)) {
			Packet230ModLoader packet = BuildCraftCore.bufferedDescriptions.get(index);
			BuildCraftCore.bufferedDescriptions.remove(index);
			
			handleDescriptionPacket(packet);
		}
	}

}

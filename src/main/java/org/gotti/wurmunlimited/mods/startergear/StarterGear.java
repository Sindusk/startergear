package org.gotti.wurmunlimited.mods.startergear;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.gotti.wurmunlimited.modloader.classhooks.HookException;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemList;
import com.wurmonline.server.players.Player;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtNewMethod;
import javassist.NotFoundException;

public class StarterGear
implements WurmServerMod,
Configurable,
PreInitable {
    boolean bDebug = false;

    HashMap<Integer, ItemInsert> weaponInserts = new HashMap<>();
    HashMap<Integer, ItemInsert> armourInserts = new HashMap<>();
    HashMap<Integer, ItemInsert> itemInserts = new HashMap<>();
    
	HashMap<String, Integer> nameToId = new HashMap<>();
	HashMap<Integer, String> idToName = new HashMap<>();
    
    private Logger logger;

    public StarterGear() {
        this.logger = Logger.getLogger(this.getClass().getName());
    }
    
    protected class ItemInsert{
    	public int templateId;
    	public float quality;
    	public int num;
    	public boolean isStarter;
    	public ItemInsert(String type, int id, String value){
    		String[] data = value.split(",");
    		if(data.length != 4){
    			Debug("Error, data for item at id "+id+" is invalid.");
    			return;
    		}
    		try{
    			templateId = Integer.valueOf(data[0]);
    		}catch(NumberFormatException e){
    			templateId = nameToId.get(data[0]);
    		}
    		quality = Float.valueOf(data[1]);
    		num = Integer.valueOf(data[2]);
    		isStarter = Boolean.valueOf(data[3]);
    		String message = "Creating new "+type+"; Raw data = ["+value+"]; template "+templateId+", quality "+quality;
    		if(isStarter){
    			message = message + ", starter";
    		}
    		message = message + ", amount "+num;
    		Debug(message);
    	}
    }

    public void configure(Properties properties) {
    	for (Field field : ItemList.class.getFields()){
    		String name = field.getName();
			try {
				int id = field.getInt(ItemList.class);
	    		nameToId.put(name, id);
	    		idToName.put(id, name);
			} catch (IllegalArgumentException | IllegalAccessException e) {
				e.printStackTrace();
			}
    	}
    	this.bDebug = Boolean.parseBoolean(properties.getProperty("debug", Boolean.toString(this.bDebug)));
    	for (String name : properties.stringPropertyNames()) {
            try {
                String value = properties.getProperty(name);
                switch (name) {
                    case "debug":
                    case "classname":
                    case "classpath":
                    case "sharedClassLoader":
                        break; //ignore
                    default:
                        if (name.startsWith("weapon-")) {
                            String[] split = name.split("-");
                            int id = Integer.parseInt(split[1]);
                            weaponInserts.put(id, new ItemInsert("Weapon", id, value));
                        } else if (name.startsWith("armour-")) {
                            String[] split = name.split("-");
                            int id = Integer.parseInt(split[1]);
                            armourInserts.put(id, new ItemInsert("Armour", id, value));
                        } else if (name.startsWith("item-")) {
                            String[] split = name.split("-");
                            int id = Integer.parseInt(split[1]);
                            itemInserts.put(id, new ItemInsert("Item", id, value));
                        } else {
                            Debug("Unknown config property: " + name);
                        }
                }
            } catch (Exception e) {
                Debug("Error processing property " + name);
            }
        }
    	Debug("Loaded "+properties.stringPropertyNames().size()+" starter item entries.");
        try {
            String logsPath = Paths.get("mods", new String[0]) + "/logs/";
            File newDirectory = new File(logsPath);
            if (!newDirectory.exists()) {
                newDirectory.mkdirs();
            }
            FileHandler fh = new FileHandler(String.valueOf(String.valueOf(logsPath)) + this.getClass().getSimpleName() + ".log", 10240000, 200, true);
            if (this.bDebug) {
                fh.setLevel(Level.INFO);
            } else {
                fh.setLevel(Level.WARNING);
            }
            fh.setFormatter(new SimpleFormatter());
            this.logger.addHandler(fh);
        }
        catch (IOException ie) {
            System.err.println(String.valueOf(this.getClass().getName()) + ": Unable to add file handler to logger");
        }
        this.Debug("Debugging messages are enabled.");
    }

    private void Debug(String x) {
        if (this.bDebug) {
            System.out.println(String.valueOf(this.getClass().getSimpleName()) + ": " + x);
            System.out.flush();
            this.logger.log(Level.INFO, x);
        }
    }
    
    // Sets AuxData to tag it as a "starter" item.
    public Item insertStarterItem(Player player, int templateId, float qualityLevel) throws Exception{
    	Item inventory = player.getInventory();
    	Item c = Player.createItem(templateId, qualityLevel);
    	c.setAuxData((byte)1);
    	inventory.insertItem(c);
    	return c;
    }
    
    // Does not set the AuxData that makes it a "starter" item.
    public Item insertRealItem(Player player, int templateId, float qualityLevel) throws Exception{
    	Item inventory = player.getInventory();
    	Item c = Player.createItem(templateId, qualityLevel);
    	inventory.insertItem(c);
    	return c;
    }
    
    private String getItemInsertString(ItemInsert item){
    	String toReturn = "";
    	for(int x = 0; x < item.num; x++){
			if(item.isStarter){
				toReturn = toReturn + "  insertStarterItem";
			}else{
				toReturn = toReturn + "  insertRealItem";
			}
			toReturn = toReturn + "(this, "+item.templateId+", (float)"+item.quality+");";
		}
    	return toReturn;
    }

    public void preInit() {
        try {
        	ClassPool classPool = HookManager.getInstance().getClassPool();
	        classPool.appendClassPath("./mods/startergear/startergear.jar");
            CtClass ctPlayer = classPool.get("com.wurmonline.server.players.Player");
	        CtClass ctStarterGear = classPool.get(this.getClass().getName());
	        ctPlayer.addMethod(CtNewMethod.copy(ctStarterGear.getDeclaredMethod("insertStarterItem"), ctPlayer, null));
	        ctPlayer.addMethod(CtNewMethod.copy(ctStarterGear.getDeclaredMethod("insertRealItem"), ctPlayer, null));
	        //ctPlayer.addMethod(CtNewMethod.copy(ctStarterGear.getDeclaredMethod("createTestItems"), ctPlayer, null));
	        //ctPlayer.addMethod(CtNewMethod.copy(ctStarterGear.getDeclaredMethod("createChallengeItems"), ctPlayer, null));
	        //ctPlayer.addMethod(CtNewMethod.copy(ctStarterGear.getDeclaredMethod("createNewPlayerItems"), ctPlayer, null));
	        
	        String newBody = "{";
    		for(Integer i : weaponInserts.keySet()){
    			newBody = newBody + getItemInsertString(weaponInserts.get(i));
    		}
    		for(Integer i : armourInserts.keySet()){
    			newBody = newBody + getItemInsertString(armourInserts.get(i));
    		}
    		newBody = newBody + "  this.wearItems();";
    		for(Integer i : itemInserts.keySet()){
    			newBody = newBody + getItemInsertString(itemInserts.get(i));
    		}
    		newBody = newBody + "}";
	        
            ctPlayer.getDeclaredMethod("createSomeItems").setBody(newBody);
        }
        catch (CannotCompileException | NotFoundException e) {
            throw new HookException((Throwable)e);
        }
    }

}
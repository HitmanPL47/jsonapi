package com.alecgorge.minecraft.jsonapi.adminium;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.json.simple.JSONObject;

import com.alecgorge.java.http.HttpRequest;
import com.alecgorge.minecraft.jsonapi.JSONAPI;
import com.alecgorge.minecraft.jsonapi.api.APIMethodName;
import com.alecgorge.minecraft.jsonapi.api.JSONAPICallHandler;
import com.alecgorge.minecraft.jsonapi.api.JSONAPIStream;
import com.alecgorge.minecraft.jsonapi.api.JSONAPIStreamListener;
import com.alecgorge.minecraft.jsonapi.api.JSONAPIStreamMessage;
import com.alecgorge.minecraft.jsonapi.streams.ConnectionMessage;

public class PushNotificationDaemon implements JSONAPIStreamListener, JSONAPICallHandler {
	YamlConfiguration deviceConfig = new YamlConfiguration();
	File configFile;
	
	List<String> devices;
	Map<String, Boolean> settings = new HashMap<String, Boolean>();
	
	private final String APNS_PUSH_ENDPOINT = "http://alecgorge.com:25132/push";
	
	private JSONAPI api;
	public boolean init = false;
	
	public boolean doTrace = true;
	
	private Logger mcLog = Logger.getLogger("Minecraft");
	
	private void trace(Object ... args) {		
		if(doTrace) {
			String[] na = new String[args.length];
			for(int i = 0; i < args.length; i++)
				na[i] = args[i] == null ? "NULL_VALUE" : args[i].toString();
			
			mcLog.info("'" + api.join(Arrays.asList(na), "' '") + "'");
		}
	}
	
	private List<String> pushTypes = new ArrayList<String>();
	private List<String> pushTypeDescriptions;
	
	public PushNotificationDaemon(File configFile, JSONAPI api) throws FileNotFoundException, IOException, InvalidConfigurationException {
		this.configFile = configFile;
		this.api = api;
				
		api.registerAPICallHandler(this);
		if(configFile.exists()) {
			initalize();
		}
	}
	
	public void addDeviceIfNotExist(String device) throws IOException {
		if(!devices.contains(device)) {
			registerDevice(device);
		}
	}
	
	private void registerDevice(final String device) {
		trace("Attempting to register", device);
		
		if(device.length() != 64) {
			return;
		}
		
    	devices.add(device);
    	deviceConfig.set("devices", devices);
    	try {
			deviceConfig.save(configFile);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void onMessage(JSONAPIStreamMessage message, JSONAPIStream sender) {
		if(message instanceof ConnectionMessage) {
			ConnectionMessage c = (ConnectionMessage) message;
			if(settings.get("player_joined") && c.TrueIsConnectedFalseIsDisconnected) {
				pushNotification(c.player + " joined!");
			}
			else if(settings.get("player_quit") && !c.TrueIsConnectedFalseIsDisconnected) {
				pushNotification(c.player + " quit!");
			}
		}
	}
	
	public void pushNotification(final String message) {
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				trace("pushing", message);
				HttpRequest r = null;
				try {
					r = new HttpRequest(new URL(APNS_PUSH_ENDPOINT));
			        for(String d : devices) {
			        	r.addPostValue("devices[]", d);
			        }
			        r.addPostValue("message", message);
			        
			        trace("Sending Post Args:", devices, message, r.getPostKeys(), r.getPostValues());
			
			        trace("Complete", r.post().getReponse());
				} catch (Exception e) {
					e.printStackTrace();
				}
				finally {
					if(r!=null) r.close();
				}
			}
		}).start();		
	}

	@Override
	public boolean willHandle(APIMethodName methodName) {
		return (methodName.getNamespace().equals("adminium") && (methodName.getMethodName().equals("registerDevice") || methodName.getMethodName().equals("listPushTypes")));
	}
	
	private void initalize() {
		if(!this.init) {
			boolean initialSetup = !configFile.exists();
			
			try {
				configFile.createNewFile();
				deviceConfig.load(configFile);
				
				if(initialSetup) {
					deviceConfig.set("devices", null);
					deviceConfig.set("settings", "");
					deviceConfig.set("settings.player_joined", true);
					deviceConfig.set("settings.player_quit", true);
					deviceConfig.set("settings.admin_call", true);
					deviceConfig.set("settings.severe_log", true);
					
					deviceConfig.save(configFile);
					deviceConfig.load(configFile);
				}
				
				devices = deviceConfig.getList("devices", new ArrayList<String>());
				
				trace("Current Devices", devices);
				
				Map<String, Object> tempDefaults = ((ConfigurationSection)deviceConfig.get("settings")).getValues(false);
				for(String key : tempDefaults.keySet()) {
					settings.put(key, Boolean.valueOf(tempDefaults.get(key).toString()));
				}
				
				if(settings.get("player_joined") || settings.get("player_quit")) {
					api.getStreamManager().getStream("connections").registerListener(this, false);
				}				
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			pushTypes.addAll(settings.keySet());
			Collections.sort(pushTypes);
			pushTypeDescriptions = Arrays.asList(new String[] {
				"A notification with the caller's name and reason when someone runs the command /calladmin.",
				"A notification when someone joins the server.",
				"A notification when someone leaves the server.",
				"A notification with part of the error whenever a SEVERE log is identified."
			});
			
			this.init = true;
		}		
	}

	@Override
	public Object handle(APIMethodName methodName, Object[] args) {
		if(methodName.getNamespace().equals("adminium")) {
			initalize();
		}
		
		
		if(methodName.getNamespace().equals("adminium") && methodName.getMethodName().equals("registerDevice")) {			
			String deviceToken = args[0].toString();
			
			registerDevice(deviceToken);
		}
		else if(methodName.getNamespace().equals("adminium") && methodName.getMethodName().equals("listPushTypes")) {
			JSONObject o = new JSONObject();
			
			int i = 0;
			for(String k : pushTypes) {
				o.put(k, pushTypeDescriptions.get(i));
				i++;
			}
			
			return o;
		}
		return null;
	}
}

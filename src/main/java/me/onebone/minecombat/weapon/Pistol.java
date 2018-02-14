package me.onebone.minecombat.weapon;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.HandlerList;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerItemHeldEvent;
import cn.nukkit.item.Item;
import me.onebone.minecombat.MineCombat;
import me.onebone.minecombat.Participant;

public class Pistol extends Gun implements Listener{
	public Pistol(MineCombat plugin, Participant player) {
		super(plugin, player, 30, 120);
		
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
	}
	
	@Override
	public int getShootInterval(){
		return 5;
	}
	
	@Override
	public String getName(){
		return "Pistol";
	}
	
	@EventHandler
	public void onPlayerItemHeld(PlayerItemHeldEvent event){
		Player player = event.getPlayer();
		
		if(player == this.getHolder().getPlayer()){
			if(event.getItem().getId() == Item.PUMPKIN_STEM){
				this.setHolding(true);
			}else{
				this.setHolding(false);
				this.isShooting = false;
			}
		}
	}
	
	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event){
		Player player = event.getPlayer();
		
		if(player == this.getHolder().getPlayer()){
			if(event.getItem().getId() == Item.PUMPKIN_STEM){
				this.isShooting = !this.isShooting;
			}
		}
	}
	
	@Override
	public void pause(){
		HandlerList.unregisterAll(this);
		
		super.pause();
	}
	
	@Override
	public void resume(){
		this.plugin.getServer().getPluginManager().registerEvents(this, plugin);
		
		super.resume();
	}
	
	@Override
	public void close(){
		HandlerList.unregisterAll(this);
		
		super.close();
	}
}

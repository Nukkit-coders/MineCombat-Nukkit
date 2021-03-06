package me.onebone.minecombat.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.inventory.InventoryPickupItemEvent;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.player.PlayerDeathEvent;
import cn.nukkit.event.player.PlayerRespawnEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Position;
import cn.nukkit.scheduler.PluginTask;
import cn.nukkit.utils.TextFormat;
import me.onebone.minecombat.MineCombat;
import me.onebone.minecombat.Participant;
import me.onebone.minecombat.weapon.AK47;
import me.onebone.minecombat.weapon.Gun;
import me.onebone.minecombat.weapon.Weapon;

public class GunMatch extends Game implements Listener{
	private Map<String, List<Weapon>> weapons = new HashMap<>();
	private Map<String, Integer> prevTeam = new HashMap<>();
	private List<Player> immortal = new LinkedList<>();
	
	public GunMatch(MineCombat plugin, String name, Position[] position, Position[] spawns){
		super(plugin, name, position, spawns);
		
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
	}
	
	@EventHandler
	public void onItemPickup(InventoryPickupItemEvent event){
		if(event.getItem().getItem().getId() == Item.MELON_STEM && event.getInventory().getHolder() instanceof Player){
			Player player = (Player) event.getInventory().getHolder();
			
			Participant participant = plugin.getParticipant(player);
			if(participant != null && participant.getJoinedGame() == this){
				participant.getArmed().forEach((weapon) -> {
					if(weapon instanceof Gun){
						((Gun) weapon).addMagazine(((Gun) weapon).getDefaultLoaded());
					}
				});
			}
		}
		
		event.setCancelled();
		event.getItem().kill();
	}
	
	@Override
	public void respawnParticipant(PlayerRespawnEvent event, Participant participant){
		super.respawnParticipant(event, participant);
		
		this.immortal.add(participant.getPlayer());
		
		this.plugin.getServer().getScheduler().scheduleDelayedTask(new PluginTask<MineCombat>(plugin){
			@Override
			public void onRun(int currentTick){
				GunMatch.this.immortal.remove(participant.getPlayer());
			}
		}, 20 * 10);
	}
	
	@EventHandler
	public void onEntityDamage(EntityDamageEvent event){
		if(this.immortal.contains(event.getEntity())){
			event.setCancelled();
		}
	}
	
	@Override
	public String getScoreMessage(Participant participant){
		String[] teams = this.getTeams();

		int time = this.getLeftTicks();
		
		Gun gun = null;
		for(Weapon weapon : participant.getArmed()){
			if(weapon instanceof Gun){
				gun = (Gun) weapon;
				break;
			}
		}
		
		String gunInfo = TextFormat.RED + "N/A" + TextFormat.WHITE;
		if(gun != null){
			int loaded = gun.getLoaded();
			gunInfo = TextFormat.RED + gun.getName() + TextFormat.WHITE + " > " + 
					(loaded < ((double) gun.getMaxLoaded() / 100D) ? 
						TextFormat.RED + "" + loaded : TextFormat.GREEN + "" + loaded) + TextFormat.WHITE + " / " + TextFormat.YELLOW + gun.getMagazine() + TextFormat.WHITE;
		}
		
		return this.getMode() == MineCombat.MODE_ONGOING ? 
				this.plugin.getMessage("gunmatch.info.ongoing",
				(time < 20*10 ? TextFormat.RED : TextFormat.GREEN) + "" + this.getTimeString(time) + TextFormat.WHITE,
				teams[participant.getTeam()], this.getScoreString(participant), gunInfo)
				: this.plugin.getMessage("gunmatch.info.standby",
						(time < 20*10 ? TextFormat.RED : TextFormat.GREEN) + "" + this.getTimeString(time) + TextFormat.WHITE,
						teams[participant.getTeam()], this.getScoreString(participant),
						gunInfo);
	}
	
	private void giveItem(Participant participant){
		participant.getPlayer().getInventory().clearAll();
		participant.getPlayer().getInventory().addItem(new Item(Item.MELON_STEM));
		participant.getPlayer().getInventory().setHeldItemSlot(
			participant.getPlayer().getInventory().first(new Item(Item.MELON_STEM))
		);
	}
	
	@Override
	public void onParticipantKilled(PlayerDeathEvent event, Participant participant){
		participant.getArmed().forEach(weapon -> {
			if(weapon instanceof Gun){
				((Gun) weapon).resetAmmo();
				((Gun) weapon).isShooting = false;
			}
		});
		
		if(this.getMode() == MineCombat.MODE_ONGOING){
			EntityDamageEvent dev = event.getEntity().getLastDamageCause();
			if(dev instanceof EntityDamageByEntityEvent){
				Entity damager = ((EntityDamageByEntityEvent) dev).getDamager();
				if(damager instanceof Player){
					Participant cause = plugin.getParticipant((Player) damager);
					if(!this.isColleague(cause, participant)){
						this.addTeamScore(cause.getTeam());
					}
					
					this.addMessage(new KillNotice(cause.getPlayer().getName(), cause.getTeam(), participant.getPlayer().getName(), participant.getTeam()));
				}
			}
		}
				
		event.setDeathMessage("");
		event.setKeepInventory(true);
		event.getEntity().getLevel().dropItem(event.getEntity(), new Item(Item.MELON_STEM));
		
		this.giveItem(participant);
	}

	@Override
	public boolean startGame(List<Participant> players){
		for(Participant participant : players){
			participant.getArmed().forEach(weapon -> {
				if(weapon instanceof Gun){
					((Gun) weapon).resetAmmo();
				}
			});
		}
		
		return true;
	}

	@Override
	public boolean standBy(List<Participant> players){
		this.selectTeams();
		
		this.sendNameTagAll();
		return true;
	}
	
	@Override
	public boolean addPlayer(Participant player){
		String username = player.getPlayer().getName().toLowerCase();
		
		if(weapons.containsKey(username)){
			player.setArmed(this.weapons.get(username));
		}else{
			player.armWeapon(new AK47(plugin, player));
		}
		
		if(super.addPlayer(player)){
			weapons.remove(username);
			
			this.giveItem(player);
			
			if(prevTeam.containsKey(username)){
				int team = prevTeam.get(username);
				
				this.setPlayerTeam(player, team);
				
				this.prevTeam.remove(username);
			}else{
				this.selectTeam(player);
			}
			
			this.sendNameTag(player);
			
			return true;
		}
		return false;
	}
	
	@Override
	public boolean removePlayer(Participant player){
		if(super.removePlayer(player)){
			player.getArmed().forEach(weapon -> {
				if(weapon instanceof Gun){
					((Gun) weapon).setHolding(false);
					((Gun) weapon).isShooting = false;
				}
			});
			this.weapons.put(player.getPlayer().getName().toLowerCase(), new ArrayList<Weapon>(player.getArmed()));
			this.prevTeam.put(player.getPlayer().getName().toLowerCase(), player.getTeam());
			
			player.dearmAll();
			
			return true;
		}
		return false;
	}

	@Override
	public void stopGame(){
		prevTeam.clear();
		weapons.clear();
	}

	@Override
	public void closeGame(){}

	@Override
	public boolean onParticipantMove(Participant player){
		return true;
	}
}

package me.onebone.minecombat.weapon;

import java.util.ArrayList;
import java.util.List;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.particle.DustParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.network.protocol.ExplodePacket;
import me.onebone.minecombat.MineCombat;
import me.onebone.minecombat.Participant;
import me.onebone.minecombat.event.EntityDamageByGunEvent;

public abstract class Gun extends Weapon{

	public boolean isShooting = false;
	protected Item gunItem = Item.get(Item.MELON_STEM);

	private int loaded, magazine, defaultLoaded, defaultMagazine;

	protected long lastShoot = 0;

	private static ShootThread thr = null;

	public Gun(MineCombat plugin, Participant player, int loaded, int magazine){
		super(plugin, player);

		this.defaultLoaded = this.loaded = loaded;
		this.defaultMagazine = this.magazine = magazine;

		if(thr == null){
			thr = new ShootThread();
			thr.start();
		}

		thr.addGun(this);
	}

	public void addLoaded(int amount){
		this.loaded += amount;
	}

	public void addMagazine(int amount){
		this.magazine += amount;
	}

	public int getLoaded(){
		return this.loaded;
	}

	public int getMagazine(){
		return this.magazine;
	}

	public int getDefaultLoaded(){
		return this.defaultLoaded;
	}

	public int getDefaultMagazine(){
		return this.defaultMagazine;
	}

	public int reloadAmmo(){
		int load = Math.min(this.getMaxLoaded() - this.loaded, this.magazine);
		if(load <= 0){
			return this.loaded;
		}

		this.magazine -= load;
		this.loaded += load;

		return this.loaded;
	}

	public void resetAmmo(){
		this.loaded = this.defaultLoaded;
		this.magazine = this.defaultMagazine;
	}

	/**
	 * @return Shoot interval in tick
	 */
	public int getShootInterval(){
		return 20;
	}

	public boolean isShooting(){
		return this.isShooting;
	}

	public boolean canShoot(){
		return (System.currentTimeMillis() - this.lastShoot) > this.getShootInterval() * 50;
	}

	public int getMaxLoaded(){
		return 10;
	}

	public int getHitDamage(double distance){
		return 1;
	}

	public int getShotDamage(double distance){
		return 5;
	}

	public int getHeadshotDamage(double distance){
		return 20;
	}

	public int getRange(){
		return 30;
	}

	public abstract String getName();

	public boolean canHit(Vector3 vec, Participant participant){
		Player player = participant.getPlayer();

		return !this.getHolder().getJoinedGame().isColleague(participant, this.getHolder()) && (player.getX() - 1 <= vec.getX() && vec.getX() <= player.getX() + 1
				&& player.getY() <= vec.getY() && vec.getY() <= player.getY() + player.getHeight()
				&& player.getZ() - 1 <= vec.getZ() && vec.getZ() <= player.getZ() + 1);
	}

	public boolean isHeadshot(Vector3 vec, Participant participant){
		Player player = participant.getPlayer();

		return (player.getX() - 1 <= vec.getX() && vec.getX() <= player.getX() + 1
				&& player.getY() + player.getEyeHeight() <= vec.getY() && vec.getY() <= player.getY() + player.getHeight()
				&& player.getZ() - 1 <= vec.getZ() && vec.getZ() <= player.getZ() + 1);
	}

	public boolean shoot(){
		if(this.loaded <= 0){
			if(this.reloadAmmo() == 0){
				return false;
			}
		}

		Player owner = this.getHolder().getPlayer();
		if(owner != null){
			this.lastShoot = System.currentTimeMillis();
			this.loaded--;

			Level level = owner.getLevel();
			double _x = owner.getX();
			double _y = owner.getY() + owner.getEyeHeight();
			double _z = owner.getZ();

			ExplodePacket pk = new ExplodePacket();
			pk.x = (float) _x;
			pk.y = (float) _y;
			pk.z = (float) _z;
			pk.radius = 0.1F;
			pk.records = new Vector3[]{};
			Participant[] players = this.getHolder().getJoinedGame().getParticipants().stream().filter(participant -> {
				if(this.getHolder().getPlayer().getLevel().getPlayers().containsValue(participant.getPlayer())){
					participant.getPlayer().dataPacket(pk);
					return true;
				}
				return false;
			}).toArray(Participant[]::new);

			double xcos = Math.cos((owner.getYaw() - 90) / 180 * Math.PI);
			double zsin = Math.sin((owner.getYaw() - 90) / 180 * Math.PI);
			double pcos = Math.cos((owner.getPitch() + 90) / 180 * Math.PI);

			for(int c = 0; c < this.getRange(); c++){
				Vector3 vec = new Vector3(_x - (c * xcos), _y + (c * pcos), _z - (c * zsin));
				level.addParticle(new DustParticle(vec, 0xb3, 0xb3, 0xb3));

				if(level.getBlock(new Vector3(Math.floor(vec.x), Math.floor(vec.y), Math.floor(vec.z))).isSolid()) return true;

				for(Participant player : players){
					if(player.getPlayer() == owner) continue;

					if(this.canHit(vec, player)){
						if(this.isHeadshot(vec, player)){
							player.getPlayer().attack(new EntityDamageByGunEvent(this.getHolder(), player, EntityDamageByGunEvent.DamageCause.CUSTOM, this.getHeadshotDamage((owner.distance(player.getPlayer())))));
						}else{
							player.getPlayer().attack(new EntityDamageByGunEvent(this.getHolder(), player, EntityDamageByGunEvent.DamageCause.CUSTOM, this.getShotDamage(owner.distance(player.getPlayer()))));
						}
					}
				}
			}
		}
		return true;
	}

	@Override
	public void attack(Entity entity){}

	@Override
	public void pause(){
		thr.removeGun(this);

		super.pause();
	}

	@Override
	public void resume(){
		thr.addGun(this);

		super.resume();
	}

	@Override
	public void close(){
		thr.removeGun(this);

		super.close();
	}

	private class ShootThread extends Thread{
		public List<Gun> guns = new ArrayList<>();

		public void addGun(Gun gun){
			guns.add(gun);
		}

		public boolean removeGun(Gun gun){
			return guns.remove(gun);
		}

		@Override
		public void run(){
			while(true){
				try{
					for(Gun gun : guns){
						if(gun.isShooting && gun.canShoot()){
							gun.shoot();
						}
					}
				}catch(Exception e){
					// ignore
				}

				try{
					Thread.sleep(50);
				}catch(InterruptedException e){

				}
			}
		}
	}
}

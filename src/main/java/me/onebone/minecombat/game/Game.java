package me.onebone.minecombat.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import cn.nukkit.event.player.PlayerDeathEvent;
import cn.nukkit.event.player.PlayerRespawnEvent;
import cn.nukkit.level.Position;
import cn.nukkit.scheduler.PluginTask;
import cn.nukkit.utils.TextFormat;
import me.onebone.minecombat.MineCombat;
import me.onebone.minecombat.Participant;
import me.onebone.minecombat.weapon.Weapon;

public abstract class Game {
	protected MineCombat plugin;

	protected Position[] position, spawns;
	protected List<Participant> players = new ArrayList<>();
	protected Map<Integer, List<Participant>> teams = new HashMap<>();
	
	private List<KillNotice> messages = new LinkedList<>();

	private int[] score;
	private int mode = MineCombat.MODE_STANDBY;
	private final String name;
	private int taskId = -1;
	private long startTime = 0;

	private int currentGame = 0;

	/**
	 * @param plugin
	 * @param name
	 *            Name of game
	 * @param position
	 *            Position of game field. `null` will given if unlimited.
	 */
	public Game(MineCombat plugin, String name, Position[] position, Position[] spawns) {
		this.plugin = plugin;

		this.name = name;
		this.position = position;
		this.spawns = spawns;

		int count = this.getTeamCount();
		this.score = new int[count];

		for (int i = 0; i < count; i++) {
			teams.put(i, new ArrayList<Participant>());
		}

		this.showScore();
	}

	public String getName() {
		return this.name;
	}
	
	public void addMessage(KillNotice notice){
		this.messages.add(notice);
	}

	public void showScore() {
		this.taskId = this.plugin.getServer().getScheduler()
				.scheduleDelayedRepeatingTask(new PluginTask<MineCombat>(this.plugin) {
					public void onRun(int currentTick) {
						for (Participant player : Game.this.getParticipants()) {
							player.getPlayer().sendTip(Game.this.getScoreMessage(player));
							
							StringBuilder builder = new StringBuilder();
							Iterator<KillNotice> itr = Game.this.messages.iterator();
							while(itr.hasNext()){
								KillNotice msg = itr.next();
								
								if(msg.isExpired()){
									itr.remove();
									continue;
								}
								builder.append(msg.compose(player.getTeam()) + "\n");
							}
							
							player.getPlayer().sendPopup(builder.toString());
						}
					}
				}, 10, 10).getTaskId();
	}

	public String getScoreMessage(Participant participant) {
		String[] teams = this.getTeams();

		int time = this.getLeftTicks();

		return this.mode == MineCombat.MODE_ONGOING
				? this.plugin.getMessage("game.info.ongoing",
						(time < 20 * 10 ? TextFormat.RED : TextFormat.GREEN) + "" + this.getTimeString(time)
								+ TextFormat.WHITE,
						teams[participant.getTeam()], this.getScoreString(participant))
				: this.plugin.getMessage(
						"game.info.standby", (time < 20 * 10 ? TextFormat.RED : TextFormat.GREEN) + ""
								+ this.getTimeString(time) + TextFormat.WHITE,
						teams[participant.getTeam()], this.getScoreString(participant));
	}

	protected String getTimeString(int tick) {
		tick /= 20;

		return String.format("%02d", tick / 60) + ":" + String.format("%02d", tick % 60);
	}

	protected String getScoreString(Participant participant) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < this.score.length; i++) {
			if (i > 0)
				builder.append(" / ");
			builder.append((i == participant.getTeam() ? TextFormat.GREEN : TextFormat.RED) + "" + score[i]
					+ TextFormat.WHITE);
		}

		return builder.toString();
	}

	public void cancelShowScore() {
		if (this.taskId >= 0 && this.plugin.getServer().getScheduler().isQueued(this.taskId)) {
			this.plugin.getServer().getScheduler().cancelTask(taskId);
		}
	}

	public int getLeftTicks() {
		return this.mode == MineCombat.MODE_ONGOING ? this.getGameTime() - this.getElapsedTicks()
				: this.getStandByTime() - this.getElapsedTicks();
	}

	public int getElapsedTicks() {
		return (int) ((System.currentTimeMillis() - this.startTime) / 50);
	}

	/**
	 * @return Stand by time in tick. Returns <= 0 if none.
	 */
	public int getStandByTime() {
		return 20 * 60; // 1 min
	}

	/**
	 * @return Game time in tick. Returns <= 0 if unlimited.
	 */
	public int getGameTime() {
		return 20 * 60 * 15; // 15 min
	}

	public String[] getTeams() {
		return new String[] { this.plugin.getMessage("game.team.red"), this.plugin.getMessage("game.team.blue") };
	}

	public boolean isColleague(Participant one, Participant two) {
		return this.mode == MineCombat.MODE_STANDBY || one.getTeam() == two.getTeam();
	}

	public void onParticipantKilled(PlayerDeathEvent event, Participant participant) {

	}

	/**
	 * Called when server is sending name tag of player
	 * 
	 * @param to
	 *            Player to send
	 * @param of
	 *            Target player
	 * @return name
	 */
	public String onSetNameTag(Participant to, Participant of) {
		return (this.mode == MineCombat.MODE_STANDBY ? TextFormat.YELLOW
				: (this.isColleague(to, of) ? TextFormat.GREEN : TextFormat.RED)) + of.getPlayer().getName();
	}

	public void respawnParticipant(PlayerRespawnEvent event, Participant player) {
		int team = player.getTeam();

		if (team >= 0 && team < this.spawns.length && this.spawns[team] != null) {
			if(event != null){
				event.setRespawnPosition(this.spawns[team]);
			}
			player.getPlayer().setSpawn(this.spawns[team]);
			player.getPlayer().teleport(this.spawns[team]);
		}
	}

	public void respawnParticipant(Participant player) {
		this.respawnParticipant(null, player);
	}

	public int getCurrentGame() {
		return this.currentGame;
	}
	
	protected void sendNameTag(Participant player){
		for (Participant participant : this.players) {
			if (participant != player) {
				player.getPlayer().sendData(participant.getPlayer());
				participant.getPlayer().sendData(player.getPlayer());
			}
		}
	}
	
	protected void sendNameTagAll(){
		for (Participant to : this.players) {
			this.respawnParticipant(to);
			
			for (Participant of : this.players) {
				if (to != of) {
					to.getPlayer().sendData(of.getPlayer());
				}
			}
		}
		
	}

	protected void selectTeams() {
		List<Participant> cloned = new ArrayList<Participant>(players);
		Collections.shuffle(cloned);

		int count = this.getTeamCount();
		for (int i = 0; i < cloned.size(); i++) {
			Participant player = cloned.get(i);
			player.setTeam(i % count);

			this.teams.get(i % count).add(player);
		}
	}

	protected void selectTeam(Participant player) {
		int[] teams = new int[this.getTeamCount()];

		for (int i = 0; i < this.players.size(); i++) {
			if(player != this.players.get(i)){
				teams[this.players.get(i).getTeam()]++;
			}
		}

		int min = teams[0], team = 0;
		for (int i = 0; i < teams.length; i++) {
			if (teams[i] < min) {
				min = teams[i];
				team = i;
			}
		}

		this.setPlayerTeam(player, team);
	}
	
	public void setPlayerTeam(Participant participant, int team){
		if(this.teams.size() <= team){
			throw new IllegalArgumentException("Invalid team");
		}
		
		this.teams.get(team).add(participant);
		participant.setTeam(team);
	}

	public final int getTeamCount() {
		return this.getTeams().length;
	}

	public boolean addTeamScore(int team, int score) {
		if (this.score.length <= team) {
			return false;
		}

		this.score[team] += score;

		return true;
	}

	public void addTeamScore(int team) {
		this.addTeamScore(team, 1);
	}

	public void resetScore(int team) {
		this.score[team] = 0;
	}

	public void resetScores() {
		this.score = new int[this.getTeamCount()];
	}

	public final List<Participant> getParticipants() {
		return new ArrayList<Participant>(this.players);
	}

	public final int getMode() {
		return this.mode;
	}

	/**
	 * Initializes each game. Call this.selectTeams() to select teams randomly.
	 * 
	 * @param players
	 *            Initially joined participants
	 * @return `true` if successfully started, `false` if not.
	 */
	public abstract boolean startGame(List<Participant> players);

	/**
	 * Called when game is standing by
	 * 
	 * @param players
	 * @return `true` if success, `false` if not.
	 */
	public abstract boolean standBy(List<Participant> players);

	public final boolean _standBy() {
		if (this.standBy(this.players)) {
			this.startTime = System.currentTimeMillis();
			this.mode = MineCombat.MODE_STANDBY;

			return true;
		}

		return false;
	}

	public final boolean _startGame() {
		if (this.startGame(this.players)) {
			this.startTime = System.currentTimeMillis();
			this.mode = MineCombat.MODE_ONGOING;

			this.sendNameTagAll();
			
			this.resetScores();

			this.currentGame++;

			return true;
		}

		return false;
	}

	/**
	 * Called when time elasped
	 *
	 */
	public abstract void stopGame();

	public final void _stopGame() {
		this.stopGame();
		
		this.teams.values().forEach(l -> l.clear());

		this.mode = MineCombat.MODE_STANDBY;
	}

	public abstract void closeGame();

	public final void _closeGame() {
		this.closeGame();

		this.cancelShowScore();
		this.mode = -1;
	}

	/**
	 * Called when participant of game moved.
	 * 
	 * @param player
	 * @return
	 */
	public abstract boolean onParticipantMove(Participant player);

	/**
	 * Add player to game. You have to select team of player when game is
	 * ongoing.
	 * You MUST set team of player yourself.
	 * 
	 * @param player
	 * @return true if approved, false if not
	 */
	public boolean addPlayer(Participant player) {
		players.add(player);
		
		player.getArmed().forEach(weapon -> {
			if(weapon.getStatus() == Weapon.STATUS_PAUSED){
				weapon.setHolder(player);
				
				weapon.resume();
			}
		});
		
		return true;
	}

	/**
	 * Called when player left the game
	 * 
	 * @param player
	 * @return
	 */
	public boolean removePlayer(Participant player) {
		this.teams.get(player.getTeam()).remove(player);
		
		player.getArmed().forEach(weapon -> {
			if(weapon.getStatus() == Weapon.STATUS_WORKING){
				weapon.pause();
			}
		});

		return players.remove(player);
	}
}

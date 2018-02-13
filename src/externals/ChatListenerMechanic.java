package externals;

import java.lang.reflect.Field;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import com.gmail.berndivader.mythicmobsext.Main;
import com.gmail.berndivader.mythicmobsext.externals.SkillAnnotation;
import com.gmail.berndivader.mythicmobsext.utils.Utils;

import io.lumine.utils.tasks.Scheduler;
import io.lumine.utils.tasks.Scheduler.Task;
import io.lumine.xikage.mythicmobs.adapters.AbstractEntity;
import io.lumine.xikage.mythicmobs.io.MythicLineConfig;
import io.lumine.xikage.mythicmobs.skills.BuffMechanic;
import io.lumine.xikage.mythicmobs.skills.IParentSkill;
import io.lumine.xikage.mythicmobs.skills.ITargetedEntitySkill;
import io.lumine.xikage.mythicmobs.skills.Skill;
import io.lumine.xikage.mythicmobs.skills.SkillMetadata;
import io.lumine.xikage.mythicmobs.util.types.RangedDouble;

@SkillAnnotation(name="chatlistener",author="BerndiVader")
public class ChatListenerMechanic 
extends 
BuffMechanic
implements
ITargetedEntitySkill {
	int period;
	boolean cancel,breakOnMatch,breakOnFalse;
	String[]phrases;
	RangedDouble radius;
	Optional<Skill>matchSkill=Optional.empty();
	Optional<Skill>falseSkill=Optional.empty();
	Optional<Skill>inuseSkill=Optional.empty();
	
	public ChatListenerMechanic(String skill, MythicLineConfig mlc) {
		super(skill, mlc);
		this.ASYNC_SAFE=false;
		phrases=mlc.getString("phrases","").toLowerCase().split(",");
		period=mlc.getInteger("period",60);
		radius=new RangedDouble(mlc.getString("radius","<10"));
		breakOnMatch=mlc.getBoolean("breakonmatch",true);
		breakOnFalse=mlc.getBoolean("breakonfalse",false);
		this.buffName=Optional.of("chatlistener");
		String s1;
		if ((s1=mlc.getString("matchskill"))!=null) matchSkill=Utils.mythicmobs.getSkillManager().getSkill(s1);
		if ((s1=mlc.getString("falseskill"))!=null) falseSkill=Utils.mythicmobs.getSkillManager().getSkill(s1);
		if ((s1=mlc.getString("inuseskill"))!=null) inuseSkill=Utils.mythicmobs.getSkillManager().getSkill(s1);
		
	}

	@Override
	public boolean castAtEntity(SkillMetadata arg0, AbstractEntity arg1) {
		if (!arg1.isPlayer()) return false;
		System.err.println(arg0.getCaster().hasBuff(buffName.get()));
		if (!arg0.getCaster().hasBuff(this.buffName.get())) {
			try {
				BuffTracker ff=new ChatListener(this,arg0,(Player)arg1.getBukkitEntity());
				Field f=ff.getClass().getSuperclass().getDeclaredField("task");
				f.setAccessible(true);
				((ChatListener)ff).task1=(Task)f.get(ff);
			} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
				e.printStackTrace();
			}
			return true;
		}
		if (inuseSkill.isPresent()) {
			Skill sk=inuseSkill.get();
			SkillMetadata sd=arg0.deepClone();
			sk.execute(sd);
		}
		return false;
	}
	
	class ChatListener
	extends
	ChatListenerMechanic.BuffTracker
	implements
	Runnable,
	IParentSkill,
	Listener {
        final ChatListenerMechanic buff;
        Scheduler.Task task1;        
        int ticksRemaining;
        boolean hasEnded;
        Player chatter;
        
		public ChatListener(ChatListenerMechanic buff,SkillMetadata data,Player p) {
			super(data);
			this.buff=buff;
			this.data=data;
            this.ticksRemaining=buff.period;
			this.data.setCallingEvent(this);
			this.hasEnded=false;
			this.chatter=p;
			Main.pluginmanager.registerEvents(this,Main.getPlugin());
			this.start();
		}
		
        @Override
        public void run() {
            this.ticksRemaining--;
            if (data.getCaster().getEntity().isDead()||!this.hasEnded&&this.ticksRemaining<=0) {
                this.terminate();
            }
        }
        
        @EventHandler
        public void chatListener(AsyncPlayerChatEvent e) {
        	boolean bl1=phrases.length==0;
        	String s2=e.getMessage().toLowerCase();
        	Skill sk=null;
        	if(ChatListenerMechanic.this.radius.equals(
        			(double)Math.sqrt(Utils.distance3D(this.data.getCaster().getEntity().getBukkitEntity().getLocation().toVector(),
        			e.getPlayer().getLocation().toVector())))) {
        		for(int i1=0;i1<phrases.length;i1++) {
        			if ((bl1=s2.contains(phrases[i1]))) break;
        		}
        		if (bl1) {
        			if (matchSkill.isPresent()) {
        				sk=matchSkill.get();
        				sk.execute(data.deepClone());
        			}
    				if (breakOnMatch) this.terminate();
        		} else {
        			if (falseSkill.isPresent()) {
        				sk=falseSkill.get();
        				sk.execute(data.deepClone());
        			}
    				if (breakOnFalse) this.terminate();
        		}
        	}
        }
		
		@Override
		public boolean getCancelled() {
			return this.hasTerminated();
		}
		
		@Override
		public void setCancelled() {
			this.terminate();
		}
		
        @Override
        public boolean terminate() {
            if (!this.hasEnded) {
                if (ChatListenerMechanic.this.buffName.isPresent()) {
                    this.data.getCaster().unregisterBuff(ChatListenerMechanic.this.buffName.get(),this);
                }
                this.hasEnded = true;
            }
        	HandlerList.unregisterAll(this);
        	return task1.terminate();
        }
	}
	
}




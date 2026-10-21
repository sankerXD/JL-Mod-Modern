package org.snapvm.adapter;

import java.util.Date;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * java.util.Timer stand-in installed via Vm.addHostAlias("java/util/Timer").
 * ART blocks getDeclaredFields() on java.util.Timer, so the task heap is
 * unreachable by reflection — instead we record schedule params ourselves as
 * each schedule*() call passes through. Also captures the task while it is
 * mid-run (the JDK heap momentarily drops it, so heap-walking would lose the
 * game loop at exactly the wrong moment).
 */
public class SnapTimer extends Timer {

	public static final class Meta {
		public long when;
		public long period;
		public boolean fixedRate;
	}

	/** task -> schedule params; a task present here is still armed. */
	public final Map<TimerTask, Meta> armed = new IdentityHashMap<>();
	private boolean cancelled;

	public SnapTimer() {
		super();
	}

	public SnapTimer(boolean daemon) {
		super(daemon);
	}

	public SnapTimer(String name) {
		super(name);
	}

	public SnapTimer(String name, boolean daemon) {
		super(name, daemon);
	}

	private void arm(TimerTask task, long when, long period, boolean fixedRate) {
		synchronized (armed) {
			Meta m = new Meta();
			m.when = when;
			m.period = period;
			m.fixedRate = fixedRate;
			armed.put(task, m);
		}
	}

	public boolean isCancelled() {
		return cancelled;
	}

	@Override
	public void cancel() {
		synchronized (armed) {
			cancelled = true;
			armed.clear();
		}
		super.cancel();
	}

	@Override
	public int purge() {
		int n = super.purge();
		dropDead();
		return n;
	}

	/** Drop cancelled tasks, and one-shots that already fired. */
	public void dropDead() {
		synchronized (armed) {
			armed.keySet().removeIf(t -> {
				if (t instanceof SnapTimerTaskShadow) {
					SnapTimerTaskShadow s = (SnapTimerTaskShadow) t;
					return s.isGuestCancelled();
				}
				return false;
			});
		}
	}

	@Override
	public void schedule(TimerTask task, long delay) {
		arm(task, System.currentTimeMillis() + delay, -1, false);
		super.schedule(task, delay);
	}

	@Override
	public void schedule(TimerTask task, Date when) {
		arm(task, when.getTime(), -1, false);
		super.schedule(task, when);
	}

	@Override
	public void schedule(TimerTask task, long delay, long period) {
		arm(task, System.currentTimeMillis() + delay, period, false);
		super.schedule(task, delay, period);
	}

	@Override
	public void schedule(TimerTask task, Date when, long period) {
		arm(task, when.getTime(), period, false);
		super.schedule(task, when, period);
	}

	@Override
	public void scheduleAtFixedRate(TimerTask task, long delay, long period) {
		arm(task, System.currentTimeMillis() + delay, period, true);
		super.scheduleAtFixedRate(task, delay, period);
	}

	@Override
	public void scheduleAtFixedRate(TimerTask task, Date when, long period) {
		arm(task, when.getTime(), period, true);
		super.scheduleAtFixedRate(task, when, period);
	}
}

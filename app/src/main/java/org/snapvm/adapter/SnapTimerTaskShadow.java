package org.snapvm.adapter;

import android.util.Log;

import java.util.TimerTask;

import org.snapvm.GuestShadow;
import org.snapvm.Vm;
import org.snapvm.VmObject;

/**
 * Real TimerTask standing in for a guest TimerTask subclass. The host Timer's
 * own thread invokes run() on this shadow; it forwards into the guest's run()
 * via callFromHost (safe from any host thread). Inherited methods like
 * cancel()/scheduledExecutionTime() work for free — the shadow itself is the
 * scheduled object.
 */
public final class SnapTimerTaskShadow extends TimerTask implements GuestShadow {
	private static final String TAG = "SnapVm";

	private final Vm vm;
	private final VmObject guest;
	private volatile boolean guestCancelled;
	private volatile boolean ranOnce;

	public SnapTimerTaskShadow(Vm vm, VmObject guest) {
		this.vm = vm;
		this.guest = guest;
	}

	@Override
	public VmObject guest() {
		return guest;
	}

	/** TimerTask.cancel() — guest-side or JDK's own cancel-on-exception path. */
	@Override
	public boolean cancel() {
		guestCancelled = true;
		return super.cancel();
	}

	public boolean isGuestCancelled() {
		return guestCancelled;
	}

	/** True once the Timer thread has entered run() — one-shots are done then. */
	public boolean ranOnce() {
		return ranOnce;
	}

	@Override
	public void run() {
		// After a snapshot restore this shadow's guest belongs to a dead
		// epoch — self-cancel so the orphaned host Timer thread drains out
		// instead of spinning on dropped calls forever.
		if (guest.isStale()) {
			cancel();
			return;
		}
		ranOnce = true;
		try {
			vm.callFromHost(guest, "run", "()V");
		} catch (Throwable th) {
			Log.e(TAG, "guest TimerTask.run", th);
			throw th;
		}
	}
}

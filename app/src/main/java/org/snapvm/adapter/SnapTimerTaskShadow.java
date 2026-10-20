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

	public SnapTimerTaskShadow(Vm vm, VmObject guest) {
		this.vm = vm;
		this.guest = guest;
	}

	@Override
	public VmObject guest() {
		return guest;
	}

	@Override
	public void run() {
		try {
			vm.callFromHost(guest, "run", "()V");
		} catch (Throwable th) {
			Log.e(TAG, "guest TimerTask.run", th);
			throw th;
		}
	}
}

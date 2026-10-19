package org.snapvm.adapter;

import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

import org.snapvm.GuestShadow;
import org.snapvm.Vm;
import org.snapvm.VmException;
import org.snapvm.VmObject;

/**
 * Real MIDlet standing in for a guest midlet class. The AMS (MidletThread)
 * drives lifecycle on this shadow; each call forwards into the VM.
 */
public final class SnapMidletShadow extends MIDlet implements GuestShadow {
	private final Vm vm;
	private final VmObject guest;

	public SnapMidletShadow(Vm vm, VmObject guest) {
		this.vm = vm;
		this.guest = guest;
	}

	@Override
	public VmObject guest() {
		return guest;
	}

	@Override
	public void startApp() throws MIDletStateChangeException {
		call("startApp", "()V");
	}

	@Override
	public void pauseApp() {
		try {
			vm.callFromHost(guest, "pauseApp", "()V");
		} catch (VmException e) {
			throw e;
		}
	}

	@Override
	public void destroyApp(boolean unconditional) throws MIDletStateChangeException {
		call("destroyApp", "(Z)V", unconditional);
	}

	private void call(String name, String desc, Object... args) throws MIDletStateChangeException {
		try {
			vm.callFromHost(guest, name, desc, args);
		} catch (VmException e) {
			if (e.thrown instanceof MIDletStateChangeException) {
				throw (MIDletStateChangeException) e.thrown;
			}
			throw e;
		}
	}
}

package org.snapvm.adapter;

import javax.microedition.lcdui.Alert;

import org.snapvm.GuestShadow;
import org.snapvm.Vm;
import org.snapvm.VmObject;

/**
 * Real Alert standing in for a guest Alert subclass. Alert(String) delegates
 * to the four-arg ctor with nulls, so replaying with absent args as null is
 * faithful for both signatures.
 */
public final class SnapAlertShadow extends Alert implements GuestShadow {
	private final CanvasForward fwd;

	public SnapAlertShadow(Vm vm, VmObject guest) {
		super(ShadowArgs.str(guest, 1), ShadowArgs.str(guest, 2),
				ShadowArgs.img(guest, 3), ShadowArgs.alertType(guest, 4));
		this.fwd = new CanvasForward(vm, guest);
	}

	@Override
	public VmObject guest() {
		return fwd.guest();
	}

	@Override
	protected void sizeChanged(int w, int h) {
		fwd.forward2("sizeChanged", w, h);
	}
}

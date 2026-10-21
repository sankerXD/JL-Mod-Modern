package org.snapvm.adapter;

import javax.microedition.lcdui.List;

import org.snapvm.GuestShadow;
import org.snapvm.Vm;
import org.snapvm.VmObject;

/**
 * Real List standing in for a guest List subclass. The four-arg ctor rejects
 * a null element array, so a guest that called the two-arg form is replayed
 * with an empty array — identical behavior.
 */
public final class SnapListShadow extends List implements GuestShadow {
	private final CanvasForward fwd;

	public SnapListShadow(Vm vm, VmObject guest) {
		super(ShadowArgs.str(guest, 1), ShadowArgs.num(guest, 2, List.IMPLICIT),
				stringsOrEmpty(guest), ShadowArgs.images(guest, 4));
		this.fwd = new CanvasForward(vm, guest);
	}

	private static String[] stringsOrEmpty(VmObject guest) {
		String[] s = ShadowArgs.strings(guest, 3);
		return s != null ? s : new String[0];
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

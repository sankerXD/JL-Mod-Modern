package org.snapvm.adapter;

import javax.microedition.lcdui.Form;

import org.snapvm.GuestShadow;
import org.snapvm.Vm;
import org.snapvm.VmObject;

/**
 * Real Form standing in for a guest Form subclass. Form(String) and
 * Form(String, Item[]) collapse into the two-arg ctor: a null item array is
 * exactly what the one-arg ctor does.
 */
public final class SnapFormShadow extends Form implements GuestShadow {
	private final CanvasForward fwd;

	public SnapFormShadow(Vm vm, VmObject guest) {
		super(ShadowArgs.str(guest, 1), ShadowArgs.items(guest, 2));
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

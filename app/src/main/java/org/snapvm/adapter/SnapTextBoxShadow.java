package org.snapvm.adapter;

import javax.microedition.lcdui.TextBox;

import org.snapvm.GuestShadow;
import org.snapvm.Vm;
import org.snapvm.VmObject;

/**
 * Real TextBox standing in for a guest TextBox subclass. The host TextBox
 * ctor needs (title, text, maxSize, constraints) — they are replayed from the
 * guest's captured super-<init> args. A guest subclass may override
 * Displayable.sizeChanged; forward it when declared.
 */
public final class SnapTextBoxShadow extends TextBox implements GuestShadow {
	private final CanvasForward fwd;

	public SnapTextBoxShadow(Vm vm, VmObject guest) {
		super(ShadowArgs.str(guest, 1), ShadowArgs.str(guest, 2),
				ShadowArgs.num(guest, 3, 1), ShadowArgs.num(guest, 4, 0));
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

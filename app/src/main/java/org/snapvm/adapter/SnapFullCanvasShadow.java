package org.snapvm.adapter;

import javax.microedition.lcdui.Graphics;

import com.nokia.mid.ui.FullCanvas;

import org.snapvm.GuestShadow;
import org.snapvm.Vm;
import org.snapvm.VmObject;

/**
 * Real FullCanvas standing in for a guest com.nokia.mid.ui.FullCanvas
 * subclass. Same forwarding contract as SnapCanvasShadow.
 */
public final class SnapFullCanvasShadow extends FullCanvas implements GuestShadow {
	private final CanvasForward fwd;

	public SnapFullCanvasShadow(Vm vm, VmObject guest) {
		this.fwd = new CanvasForward(vm, guest);
	}

	@Override
	public VmObject guest() {
		return fwd.guest();
	}

	@Override
	protected void paint(Graphics g) {
		fwd.paint(g);
	}

	@Override
	protected void keyPressed(int keyCode) {
		fwd.forward1("keyPressed", keyCode);
	}

	@Override
	protected void keyRepeated(int keyCode) {
		fwd.forward1("keyRepeated", keyCode);
	}

	@Override
	protected void keyReleased(int keyCode) {
		fwd.forward1("keyReleased", keyCode);
	}

	@Override
	protected void pointerPressed(int x, int y) {
		fwd.forward2("pointerPressed", x, y);
	}

	@Override
	protected void pointerDragged(int x, int y) {
		fwd.forward2("pointerDragged", x, y);
	}

	@Override
	protected void pointerReleased(int x, int y) {
		fwd.forward2("pointerReleased", x, y);
	}

	@Override
	protected void showNotify() {
		fwd.forward0("showNotify");
	}

	@Override
	protected void hideNotify() {
		fwd.forward0("hideNotify");
	}

	@Override
	protected void sizeChanged(int w, int h) {
		fwd.forward2("sizeChanged", w, h);
	}
}

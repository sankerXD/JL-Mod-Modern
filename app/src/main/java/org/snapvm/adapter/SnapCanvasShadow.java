package org.snapvm.adapter;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Graphics;

import org.snapvm.GuestShadow;
import org.snapvm.Vm;
import org.snapvm.VmObject;

/**
 * Real Canvas standing in for a guest Canvas subclass. paint() is always
 * offered to the guest (abstract in Canvas); optional callbacks are forwarded
 * only when the guest class actually declares them.
 */
public final class SnapCanvasShadow extends Canvas implements GuestShadow {
	private final CanvasForward fwd;

	public SnapCanvasShadow(Vm vm, VmObject guest) {
		this.fwd = new CanvasForward(vm, guest);
	}

	@Override
	public VmObject guest() {
		return fwd.guest();
	}

	@Override
	public void suppressNextLifecycleNotify() {
		fwd.dropNextNotify();
	}

	@Override
	protected void paint(Graphics g) {
		long start = System.nanoTime();
		fwd.paint(g);
		long ms = (System.nanoTime() - start) / 1_000_000;
		SnapVm.paintProbe(ms);
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

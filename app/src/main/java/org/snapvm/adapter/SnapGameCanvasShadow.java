package org.snapvm.adapter;

import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.game.GameCanvas;

import org.snapvm.GuestShadow;
import org.snapvm.Vm;
import org.snapvm.VmObject;

/**
 * Real GameCanvas standing in for a guest GameCanvas subclass.
 * super(false) keeps command routing on; guests that constructed
 * GameCanvas(true) to suppress commands are not yet honored (the shadow
 * factory receives no ctor args).
 */
public final class SnapGameCanvasShadow extends GameCanvas implements GuestShadow {
	private final CanvasForward fwd;

	public SnapGameCanvasShadow(Vm vm, VmObject guest) {
		super(false);
		this.fwd = new CanvasForward(vm, guest);
	}

	@Override
	public VmObject guest() {
		return fwd.guest();
	}

	@Override
	public void paint(Graphics g) {
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

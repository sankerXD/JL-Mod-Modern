package org.snapvm.adapter;

import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.Sprite;

import org.snapvm.GuestShadow;
import org.snapvm.Vm;
import org.snapvm.VmObject;

/**
 * Real Sprite standing in for a guest Sprite subclass. Sprite has three
 * ctors — Image, Image+frame w/h, and a copy ctor — so the factory dispatches
 * on arg shape instead of fixing one signature up front. paint() is final in
 * Sprite: no guest callbacks exist to forward.
 */
public final class SnapSpriteShadow extends Sprite implements GuestShadow {
	private final VmObject guest;

	private SnapSpriteShadow(VmObject guest, Image img, int w, int h) {
		super(img, w, h);
		this.guest = guest;
	}

	private SnapSpriteShadow(VmObject guest, Sprite src) {
		super(src);
		this.guest = guest;
	}

	/** ShadowFactory entry: replays whichever Sprite ctor the guest invoked. */
	public static SnapSpriteShadow create(Vm vm, VmObject guest) {
		Sprite src = ShadowArgs.sprite(guest, 1);
		if (src != null) {
			return new SnapSpriteShadow(guest, src);
		}
		Image img = ShadowArgs.img(guest, 1);
		if (img == null) {
			img = Image.createImage(1, 1);
		}
		int w = ShadowArgs.num(guest, 2, img.getWidth());
		int h = ShadowArgs.num(guest, 3, img.getHeight());
		return new SnapSpriteShadow(guest, img, w, h);
	}

	@Override
	public VmObject guest() {
		return guest;
	}
}

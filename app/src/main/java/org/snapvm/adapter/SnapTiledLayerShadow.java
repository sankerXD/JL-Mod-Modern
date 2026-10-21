package org.snapvm.adapter;

import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.TiledLayer;

import org.snapvm.GuestShadow;
import org.snapvm.Vm;
import org.snapvm.VmObject;

/**
 * Real TiledLayer standing in for a guest TiledLayer subclass; single
 * (columns, rows, image, tileWidth, tileHeight) ctor replayed from the
 * captured super-<init> args.
 */
public final class SnapTiledLayerShadow extends TiledLayer implements GuestShadow {
	private final VmObject guest;

	public SnapTiledLayerShadow(Vm vm, VmObject guest) {
		super(ShadowArgs.num(guest, 1, 1), ShadowArgs.num(guest, 2, 1),
				imageOrStub(guest), ShadowArgs.num(guest, 4, 1),
				ShadowArgs.num(guest, 5, 1));
		this.guest = guest;
	}

	private static Image imageOrStub(VmObject guest) {
		Image img = ShadowArgs.img(guest, 3);
		return img != null ? img : Image.createImage(1, 1);
	}

	@Override
	public VmObject guest() {
		return guest;
	}
}

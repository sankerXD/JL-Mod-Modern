package org.snapvm.adapter;

import javax.microedition.lcdui.AlertType;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Item;
import javax.microedition.lcdui.game.Sprite;

import org.snapvm.VmObject;

/**
 * Reads a guest object's captured host-super constructor args
 * ({@link VmObject#superCtorArgs}: receiver at index 0, declared params after
 * it) and coerces them to the host types the shadow's super() call needs.
 * Missing/wrong-typed entries fall back to the caller-supplied default so a
 * shadow never dies on a malformed arg list.
 */
final class ShadowArgs {
	private ShadowArgs() {
	}

	static Object arg(VmObject g, int i) {
		Object[] a = g.superCtorArgs();
		Object v = a != null && i < a.length ? a[i] : null;
		return v instanceof VmObject ? ((VmObject) v).hostShadow : v;
	}

	static String str(VmObject g, int i) {
		Object v = arg(g, i);
		return v instanceof String ? (String) v : null;
	}

	static int num(VmObject g, int i, int def) {
		Object v = arg(g, i);
		return v instanceof Number ? ((Number) v).intValue() : def;
	}

	static Image img(VmObject g, int i) {
		Object v = arg(g, i);
		return v instanceof Image ? (Image) v : null;
	}

	static AlertType alertType(VmObject g, int i) {
		Object v = arg(g, i);
		return v instanceof AlertType ? (AlertType) v : null;
	}

	static Item[] items(VmObject g, int i) {
		Object v = arg(g, i);
		return v instanceof Item[] ? (Item[]) v : null;
	}

	static String[] strings(VmObject g, int i) {
		Object v = arg(g, i);
		return v instanceof String[] ? (String[]) v : null;
	}

	static Image[] images(VmObject g, int i) {
		Object v = arg(g, i);
		return v instanceof Image[] ? (Image[]) v : null;
	}

	static Sprite sprite(VmObject g, int i) {
		Object v = arg(g, i);
		return v instanceof Sprite ? (Sprite) v : null;
	}
}

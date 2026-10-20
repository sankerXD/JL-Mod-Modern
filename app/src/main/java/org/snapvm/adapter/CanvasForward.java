package org.snapvm.adapter;

import javax.microedition.lcdui.Graphics;

import org.snapvm.Vm;
import org.snapvm.VmObject;

/**
 * Shared guest-callback forwarding for the Canvas-family shadows
 * (Canvas / FullCanvas / GameCanvas). Optional callbacks are forwarded only
 * when the guest class actually declares them — otherwise calling into the VM
 * would bounce back to this same shadow method and recurse.
 */
final class CanvasForward {
	private static final String PAINT_DESC = "(Ljavax/microedition/lcdui/Graphics;)V";

	private final Vm vm;
	private final VmObject guest;
	private boolean dropNotify;

	CanvasForward(Vm vm, VmObject guest) {
		this.vm = vm;
		this.guest = guest;
	}

	VmObject guest() {
		return guest;
	}

	/** One-shot: swallow the next showNotify/hideNotify forwarded. */
	void dropNextNotify() {
		dropNotify = true;
	}

	void paint(Graphics g) {
		if (vm.guestDeclares(guest, "paint", PAINT_DESC)) {
			vm.callFromHost(guest, "paint", PAINT_DESC, g);
		}
	}

	void forward0(String name) {
		if (dropNotify) {
			dropNotify = false;
			return;
		}
		if (vm.guestDeclares(guest, name, "()V")) {
			vm.callFromHost(guest, name, "()V");
		}
	}

	void forward1(String name, int a) {
		android.util.Log.d("SnapVm", "fwd " + name + " " + a
				+ " declares=" + vm.guestDeclares(guest, name, "(I)V")
				+ " guest=" + guest);
		if (vm.guestDeclares(guest, name, "(I)V")) {
			vm.callFromHost(guest, name, "(I)V", a);
		}
	}

	void forward2(String name, int a, int b) {
		if (vm.guestDeclares(guest, name, "(II)V")) {
			vm.callFromHost(guest, name, "(II)V", a, b);
		}
	}
}

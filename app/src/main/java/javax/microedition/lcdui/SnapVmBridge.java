/*
 * Bridge between the SnapVM guest scheduler and the lcdui event queue.
 * Lives in this package for access to the package-private Display queue.
 */
package javax.microedition.lcdui;

import javax.microedition.lcdui.event.EventQueue;
import javax.microedition.lcdui.event.SimpleEvent;

import org.snapvm.Vm;
import org.snapvm.VmThread;

/**
 * Host-side helpers for parked guest calls: a guest call that would block a
 * real thread is converted into "queue the work, park the green thread, resume
 * when the event queue has processed it".
 */
public final class SnapVmBridge {
	private SnapVmBridge() {
	}

	/**
	 * serviceRepaints() semantics without the synchronous callbackLock wait:
	 * the repaint is queued normally and the green thread resumes once the
	 * event queue has processed everything ahead of the marker — i.e. the
	 * repaint itself. Running the wait inside the VM executor would deadlock:
	 * the queue's paint needs the executor to run the guest's paint().
	 */
	public static void serviceRepaints(Vm vm, VmThread t, Object guestCanvas, Runnable done) {
		Canvas canvas = (Canvas) vm.toHost(guestCanvas, Canvas.class);
		EventQueue eq = Display.getEventQueue();
		if (eq == null || eq.dispatchThread() == t.hostThread() || vm.inExecLock()) {
			// Two cases make the marker path unusable or pointless:
			// 1. This green thread's callFromHost originated on the event
			//    queue, which is still blocked inside that very call — a
			//    completion marker could never be consumed.
			// 2. The handler itself is running inside an execLock slice —
			//    always true for parked calls — so an inline repaint is
			//    legal: execLock is reentrant and the nested paint entry is
			//    an independent VmThread. Posting a marker instead costs a
			//    cross-thread round trip (~4-5ms on this device) per call,
			//    which dominates loading-loop tick cost.
			// armFullRepaint (not repaint()) avoids posting paintEvent — the
			// dispatch thread must never run process() concurrently with our
			// inline drain. The screen flush stays on the queue.
			canvas.armFullRepaint();
			canvas.processPendingRepaint();
			done.run();
			canvas.postFlushToScreen();
			return;
		}
		canvas.repaint();
		final long parkedAt = System.currentTimeMillis();
		Display.postEvent(new SimpleEvent() {
			@Override
			public void process() {
				org.snapvm.adapter.SnapVm.srProbe(
						System.currentTimeMillis() - parkedAt);
				done.run();
			}
		});
	}
}

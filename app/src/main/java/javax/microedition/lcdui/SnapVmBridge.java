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
		canvas.repaint();
		EventQueue eq = Display.getEventQueue();
		if (eq != null && eq.dispatchThread() == t.hostThread()) {
			// invoked inside an event-queue callback: the queue is busy with
			// this very event, so a completion marker could never be consumed
			done.run();
			return;
		}
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

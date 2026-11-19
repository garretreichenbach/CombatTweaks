package videogoose.combattweaks.utils;

import api.common.GameServer;
import api.util.StarRunnable;
import videogoose.combattweaks.CombatTweaks;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runs the order managers' periodic work on the server's main update loop instead of on private executor
 * threads. Those ticks drive AI state machines and ship physics; doing that from a background thread raced the
 * server's own AI update (and each other), so orders were intermittently dropped or overridden mid-transition.
 * Each task is gated by wall-clock interval, and only runs where a server state exists (not on remote clients).
 */
public final class ServerTicker {

	private static final class Task {
		final long intervalMs;
		final Runnable body;
		long last = System.currentTimeMillis();

		Task(long intervalMs, Runnable body) {
			this.intervalMs = intervalMs;
			this.body = body;
		}
	}

	private static final List<Task> tasks = new CopyOnWriteArrayList<>();
	private static boolean started;

	private ServerTicker() {
	}

	/** Runs {@code body} on the server thread roughly every {@code intervalMs} milliseconds. */
	public static void every(long intervalMs, Runnable body) {
		tasks.add(new Task(intervalMs, body));
		start();
	}

	private static synchronized void start() {
		if(started) {
			return;
		}
		started = true;
		new StarRunnable() {
			@Override
			public void run() {
				if(GameServer.getServerState() == null) {
					return;
				}
				long now = System.currentTimeMillis();
				for(Task task : tasks) {
					if(now - task.last < task.intervalMs) {
						continue;
					}
					task.last = now;
					try {
						task.body.run();
					} catch(Exception exception) {
						// Swallow per task: StarRunnable kills a runnable after 10 errors, which would stop every manager.
						CombatTweaks.getInstance().logException("ServerTicker task error", exception);
					}
				}
			}
		}.runTimer(CombatTweaks.getInstance(), 0);
	}
}

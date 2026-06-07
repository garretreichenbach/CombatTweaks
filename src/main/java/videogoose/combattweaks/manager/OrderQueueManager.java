package videogoose.combattweaks.manager;

import api.common.GameCommon;
import org.schema.game.common.controller.SegmentController;
import videogoose.combattweaks.CombatTweaks;
import videogoose.combattweaks.utils.AIUtils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Lets a player queue several orders for a ship — hold shift when issuing an order to append it — that
 * then run one after another.
 *
 * <p>Each ship has an optional <em>active</em> order plus a FIFO queue of pending ones. When the active
 * order completes (a move arrives, a mined asteroid is gone, an attack target is destroyed, …) the next
 * queued order starts automatically. Issuing an order <em>without</em> shift replaces the whole queue
 * (the normal one-order behaviour); Idle clears it entirely.</p>
 *
 * <p>Each order type is dispatched to the same per-type system the direct order packets use
 * ({@link MineManager}, {@link MoveManager}, {@link RepairManager}, {@link DefenseManager}, and the
 * attack helpers in {@link AIUtils}); this manager only sequences them.</p>
 */
public class OrderQueueManager {

	public enum OrderType {MOVE, MINE, ATTACK, DEFEND, REPAIR}

	private static final class QueuedOrder {
		final OrderType type;
		final int targetId;

		QueuedOrder(OrderType type, int targetId) {
			this.type = type;
			this.targetId = targetId;
		}
	}

	private static final int TICK_INTERVAL_MS = 500;
	private static OrderQueueManager instance;

	/** Pending orders per ship (the active order is held separately in {@link #active}). */
	private final Map<Integer, Deque<QueuedOrder>> queues = new ConcurrentHashMap<>();
	/** The order each ship is currently executing. */
	private final Map<Integer, QueuedOrder> active = new ConcurrentHashMap<>();
	private final ScheduledExecutorService scheduler;

	private OrderQueueManager() {
		scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "CombatTweaks-OrderQueue");
			t.setDaemon(true);
			return t;
		});
		scheduler.scheduleAtFixedRate(this::tick, TICK_INTERVAL_MS, TICK_INTERVAL_MS, TimeUnit.MILLISECONDS);
	}

	public static OrderQueueManager getInstance() {
		if(instance == null) {
			synchronized(OrderQueueManager.class) {
				if(instance == null) {
					instance = new OrderQueueManager();
				}
			}
		}
		return instance;
	}

	/** Issue an order now, discarding any active/queued orders (default, no-shift behaviour). */
	public void replace(int shipId, OrderType type, int targetId) {
		queues.remove(shipId);
		QueuedOrder order = new QueuedOrder(type, targetId);
		AIUtils.clearAllOrders(shipId);
		execute(shipId, order);
		active.put(shipId, order);
	}

	/** Append an order to run after the ship's current and already-queued orders (shift-held behaviour). */
	public void enqueue(int shipId, OrderType type, int targetId) {
		queues.computeIfAbsent(shipId, k -> new ArrayDeque<>()).add(new QueuedOrder(type, targetId));
		// Nothing running yet — start the first queued order immediately.
		if(active.get(shipId) == null) {
			advance(shipId);
		}
	}

	/** Drop all orders for a ship (e.g. on an explicit Idle order). */
	public void clear(int shipId) {
		queues.remove(shipId);
		active.remove(shipId);
	}

	/** How many orders are still queued behind the active one (for HUD/label feedback). */
	public int getQueuedCount(int shipId) {
		Deque<QueuedOrder> q = queues.get(shipId);
		return q == null ? 0 : q.size();
	}

	/**
	 * Target entity ids for this ship's whole order chain, in execution order: the active order first,
	 * then each queued order. Used by the tactical map to draw the upcoming-order path. Returns an empty
	 * array if the ship has no orders. Snapshot copy — safe to read from the render thread.
	 */
	public int[] getOrderTargetIds(int shipId) {
		java.util.List<Integer> ids = new java.util.ArrayList<>();
		QueuedOrder act = active.get(shipId);
		if(act != null) {
			ids.add(act.targetId);
		}
		Deque<QueuedOrder> q = queues.get(shipId);
		if(q != null) {
			try {
				for(QueuedOrder o : q.toArray(new QueuedOrder[0])) {
					ids.add(o.targetId);
				}
			} catch(Exception ignored) {
			}
		}
		int[] arr = new int[ids.size()];
		for(int i = 0; i < arr.length; i++) {
			arr[i] = ids.get(i);
		}
		return arr;
	}

	/** Whether the ship has an active queued order or anything pending. */
	public boolean hasOrders(int shipId) {
		return active.containsKey(shipId) || getQueuedCount(shipId) > 0;
	}

	// -------------------------------------------------------------------------

	/** Finishes the current order and starts the next queued one (or goes idle if none remain). */
	private void advance(int shipId) {
		AIUtils.clearAllOrders(shipId); // clear the order that just finished before starting the next
		Deque<QueuedOrder> q = queues.get(shipId);
		if(q != null && !q.isEmpty()) {
			QueuedOrder next = q.poll();
			execute(shipId, next);
			active.put(shipId, next);
		} else {
			active.remove(shipId);
			queues.remove(shipId);
		}
	}

	/** Hands an order to its per-type system (mirrors what the direct order packets do). */
	private void execute(int shipId, QueuedOrder order) {
		switch(order.type) {
			case MOVE:
				AIUtils.setMoveToTarget(shipId, order.targetId);
				break;
			case MINE:
				MineManager.getInstance().addMine(shipId, order.targetId);
				break;
			case ATTACK:
				AIUtils.setAttackTarget(shipId, order.targetId);
				break;
			case DEFEND:
				DefenseManager.getInstance().addDefense(shipId, order.targetId);
				break;
			case REPAIR:
				RepairManager.getInstance().addRepair(shipId, order.targetId);
				break;
		}
	}

	private void tick() {
		try {
			Iterator<Map.Entry<Integer, QueuedOrder>> it = active.entrySet().iterator();
			while(it.hasNext()) {
				Map.Entry<Integer, QueuedOrder> entry = it.next();
				int shipId = entry.getKey();
				if(!(GameCommon.getGameObject(shipId) instanceof SegmentController)) {
					it.remove();
					queues.remove(shipId); // ship gone — drop its queue
					continue;
				}
				if(isComplete(shipId, entry.getValue())) {
					advance(shipId);
				}
			}
		} catch(Exception e) {
			CombatTweaks.getInstance().logException("OrderQueue tick error", e);
		}
	}

	/** Whether the active order has finished, so the next queued one should start. */
	private boolean isComplete(int shipId, QueuedOrder order) {
		switch(order.type) {
			case MOVE:
				return MoveManager.getInstance().isArrived(shipId);
			case MINE:
				// MineManager drops the assignment once the asteroid is mined out or gone.
				return MineManager.getInstance().getAssignedTarget(shipId) == null;
			case REPAIR:
				return RepairManager.getInstance().getAssignedTarget(shipId) == null
						|| !(GameCommon.getGameObject(order.targetId) instanceof SegmentController);
			case ATTACK:
				// Target destroyed / no longer exists.
				return !(GameCommon.getGameObject(order.targetId) instanceof SegmentController);
			case DEFEND:
			default:
				// Defend is indefinite — it holds the spot, so the queue stops here until re-ordered.
				return false;
		}
	}
}

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * คิวงานที่พร้อมทำ ใช้ ReentrantLock + Condition (ไม่ busy-wait)
 *
 *  FCFS     : ตามลำดับที่ถูกใส่เข้าคิว (ticket)
 *  PRIORITY : priority น้อยก่อน -> เท่ากันดู arrivalMs ในไฟล์ -> เท่ากันดู sequence
 *             (ตัดสินจากข้อมูลของ Job ล้วน ๆ ไม่ขึ้นกับว่า Thread ไหนมาก่อน)
 *
 * คิวยังนับจำนวนงานที่กำลังทำ (running) ไว้ใน lock เดียวกัน
 * เพื่อให้ Monitor อ่าน ready/running/completed เป็นภาพเดียวกันได้
 */
public class ReadyQueue {

    private static final class Entry {
        final Job job;
        final long ticket;
        Entry(Job job, long ticket) { this.job = job; this.ticket = ticket; }
    }

    /** ภาพ ณ ขณะเดียวสำหรับ Monitor */
    public static final class Snapshot {
        public final int ready, running, completed;
        Snapshot(int ready, int running, int completed) {
            this.ready = ready; this.running = running; this.completed = completed;
        }
    }

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final PriorityQueue<Entry> queue;
    private long nextTicket = 0;
    private boolean closed = false;
    private int running = 0;

    public ReadyQueue(Config.Policy policy) {
        Comparator<Entry> cmp;
        if (policy == Config.Policy.PRIORITY) {
            cmp = Comparator.<Entry>comparingInt(e -> e.job.priority)
                    .thenComparingLong(e -> e.job.arrivalMs)
                    .thenComparingInt(e -> e.job.sequence)
                    .thenComparingLong(e -> e.ticket);
        } else {
            cmp = Comparator.comparingLong(e -> e.ticket);
        }
        this.queue = new PriorityQueue<>(cmp);
    }

    /** ใส่งานเข้าคิว เรียกโดย Scheduler */
    public void add(Job job) {
        lock.lock();
        try {
            queue.add(new Entry(job, nextTicket++));
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    /** ปิดคิว: จะไม่มีงานเข้ามาอีก ปลุก Worker ทุกตัวที่รออยู่ */
    public void close() {
        lock.lock();
        try {
            closed = true;
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /**
     * หยิบงานถัดไป รอโดยไม่กิน CPU
     * @return งาน หรือ null เมื่อคิวปิดแล้วและว่าง (Worker ควรหยุด)
     */
    public Job take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (queue.isEmpty() && !closed) {
                notEmpty.await();
            }
            Entry e = queue.poll();
            if (e == null) {
                return null;
            }
            running++;               // ย้ายจาก ready -> running ภายใน lock เดียว
            return e.job;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Worker เรียกเมื่อจบงานที่ take ไป
     * @param stats ถ้าไม่ null จะบันทึกว่างานเสร็จสมบูรณ์ (atomic กับ running--)
     */
    public void finishRunning(Job job, Statistics stats) {
        lock.lock();
        try {
            running--;
            if (stats != null) {
                stats.recordCompletion(job);
            }
        } finally {
            lock.unlock();
        }
    }

    /** จำนวนงานที่รออยู่ */
    public int size() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }

    /** อ่าน ready/running/completed พร้อมกันภายใน lock เดียว */
    public Snapshot snapshot(Statistics stats) {
        lock.lock();
        try {
            return new Snapshot(queue.size(), running, stats.completedCount());
        } finally {
            lock.unlock();
        }
    }
}

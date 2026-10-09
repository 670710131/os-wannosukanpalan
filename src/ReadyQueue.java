import java.util.PriorityQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * คิวงานที่พร้อมถูกหยิบไปทำ — ปลอดภัยต่อหลาย Thread
 *
 * การออกแบบ:
 *   - คลาสเดียวรับนโยบายผ่าน Comparator ของ PriorityQueue
 *       FCFS     : เรียงตามลำดับที่ถูกใส่เข้าคิว (ticket เพิ่มทีละ 1 ภายใน lock)
 *       PRIORITY : เรียงตาม priority (1 สูงสุด) แล้ว tie-break ดังนี้
 *                    1) arrivalMs น้อยกว่า (มาก่อนตามกำหนด) ได้ก่อน
 *                    2) sequence น้อยกว่า (อยู่บรรทัดบนในไฟล์) ได้ก่อน
 *                  ทั้งสองค่าเป็นข้อมูลของ Job เอง ไม่เกี่ยวกับว่า Thread ใดเข้าคิวก่อน
 *                  และ sequence ไม่ซ้ำกันเลย จึงตัดสินได้เสมอ (ลำดับเป็น total order)
 *   - ใช้ ReentrantLock + Condition: Worker ที่ไม่มีงานจะ await() (ถูกพัก ไม่กิน CPU)
 *   - close() คือวิธีบอก Worker ว่า "จะไม่มีงานเข้ามาอีก": take() จะส่งงานที่เหลือให้ครบก่อน
 *     แล้วคืน null เมื่อคิวว่างและถูกปิด Worker เห็น null แล้วจบ run() เอง
 *
 * ตัวนับ ready / running / completed อยู่ในคลาสนี้ภายใต้ lock เดียวกันกับคิว
 * เพื่อให้ Monitor อ่านได้เป็น snapshot ภาพเดียว (งานย้ายจาก ready -> running
 * ตอน take() และ running -> completed ตอน jobFinished() เป็นการกระทำเดียวใต้ lock)
 */
public class ReadyQueue {

    private static final class Entry {
        final Job job;
        final long ticket;
        Entry(Job job, long ticket) {
            this.job = job;
            this.ticket = ticket;
        }
    }

    private final Config.Policy policy;
    private final PriorityQueue<Entry> heap;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();

    private long nextTicket = 0;     // ป้องกันด้วย lock
    private boolean closed = false;  // ป้องกันด้วย lock
    private int running = 0;         // ป้องกันด้วย lock
    private int completed = 0;       // ป้องกันด้วย lock

    public ReadyQueue(Config.Policy policy) {
        this.policy = policy;
        if (policy == Config.Policy.FCFS) {
            heap = new PriorityQueue<>((a, b) -> Long.compare(a.ticket, b.ticket));
        } else {
            heap = new PriorityQueue<>((a, b) -> {
                int c = Integer.compare(a.job.priority, b.job.priority);
                if (c != 0) return c;
                c = Long.compare(a.job.arrivalMs, b.job.arrivalMs);
                if (c != 0) return c;
                return Integer.compare(a.job.sequence, b.job.sequence);
            });
        }
    }

    /** ใส่งานเข้าคิว เรียกโดย Scheduler Thread */
    public void add(Job job) {
        lock.lock();
        try {
            if (closed) {
                throw new IllegalStateException("ReadyQueue ถูกปิดแล้ว ใส่งาน " + job.id + " ไม่ได้");
            }
            heap.add(new Entry(job, nextTicket++));
            notEmpty.signal(); // ปลุก Worker ที่รออยู่หนึ่งตัวพอ เพราะมีงานเพิ่มชิ้นเดียว
        } finally {
            lock.unlock();
        }
    }

    /**
     * หยิบงานถัดไปตามนโยบาย เรียกโดย Worker Thread
     * รอโดยไม่กิน CPU เมื่อคิวว่าง
     *
     * @return งานถัดไป หรือ null เมื่อคิวว่างและถูก close() แล้ว (สัญญาณให้ Worker หยุด)
     */
    public Job take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (heap.isEmpty() && !closed) {
                notEmpty.await();
            }
            Entry e = heap.poll();
            if (e == null) {
                return null; // ว่างและปิดแล้ว
            }
            running++; // ready -> running ทำพร้อมกับการเอาออกจากคิว ภายใต้ lock เดียวกัน
            return e.job;
        } finally {
            lock.unlock();
        }
    }

    /** Worker เรียกเมื่อทำงานหนึ่งชิ้นจบ (running -> completed) */
    public void jobFinished() {
        lock.lock();
        try {
            running--;
            completed++;
        } finally {
            lock.unlock();
        }
    }

    /** Worker เรียกเมื่องานล้มเหลว/ถูกยกเลิกกลางคัน: ลด running โดยไม่นับว่า completed */
    public void jobAborted() {
        lock.lock();
        try {
            running--;
        } finally {
            lock.unlock();
        }
    }

    /** ประกาศว่าจะไม่มีงานเข้ามาอีก ปลุก Worker ทุกตัวที่รออยู่ให้ตรวจเงื่อนไขใหม่ */
    public void close() {
        lock.lock();
        try {
            closed = true;
            notEmpty.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /** จำนวนงานที่รออยู่ตอนนี้ */
    public int size() {
        lock.lock();
        try {
            return heap.size();
        } finally {
            lock.unlock();
        }
    }

    /** snapshot ของ {ready, running, completed} ในภาพเดียวกัน (อ่านใต้ lock เดียว) */
    public int[] snapshot() {
        lock.lock();
        try {
            return new int[] { heap.size(), running, completed };
        } finally {
            lock.unlock();
        }
    }

    public Config.Policy policy() {
        return policy;
    }
}

/**
 * Thread ที่รายงานสถานะระบบทุก ~1000 ms
 *
 * - ready/running/completed อ่านจาก ReadyQueue.snapshot() ซึ่งอ่านใต้ lock เดียว
 *   จึงเป็นภาพเดียวกัน (ผลรวมไม่เพี้ยนระหว่างงานย้ายสถานะ)
 * - สถานะ resource อ่านจาก Semaphore.availablePermits() ซึ่งปลอดภัยต่อ Thread
 *   (เป็นภาพ ณ เวลาใกล้เคียงกัน ไม่ได้อยู่ใต้ lock เดียวกับคิว)
 * - หยุดเมื่อถูก interrupt (Main เรียกหลัง Worker ทุกตัวจบแล้ว)
 */
public class Monitor extends Thread {

    private static final long INTERVAL_MS = 1000;

    private final ReadyQueue readyQueue;
    private final ResourceManager resources;
    private final ProjectLogger logger;

    public Monitor(ReadyQueue readyQueue, ResourceManager resources,
                   Statistics statistics, ProjectLogger logger) {
        super("monitor");
        this.readyQueue = readyQueue;
        this.resources = resources;
        this.logger = logger;
    }

    @Override
    public void run() {
        try {
            while (!isInterrupted()) {
                Thread.sleep(INTERVAL_MS);
                int[] s = readyQueue.snapshot();
                logger.monitor(s[0], s[1], s[2], resources.status());
            }
        } catch (InterruptedException e) {
            // ได้รับสัญญาณให้หยุด
        }
    }
}

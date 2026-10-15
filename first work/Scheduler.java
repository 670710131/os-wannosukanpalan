import java.util.concurrent.BlockingQueue;

/**
 * รับงานจาก JobGenerator (ผ่าน BlockingQueue) แล้วใส่ ReadyQueue
 * การจัดลำดับตามนโยบาย FCFS/Priority ทำที่ ReadyQueue ตอน take()
 * เมื่อได้ POISON แปลว่าไม่มีงานเข้ามาอีก -> ปิด ReadyQueue แล้วหยุด
 */
public class Scheduler extends Thread {

    /** สัญญาณ "ไม่มีงานอีกแล้ว" เทียบด้วย reference (==) */
    public static final Job POISON = new Job("__END__", 0, 1, 0, ResourceType.NONE, 0, -1);

    private final BlockingQueue<Job> inbox;
    private final ReadyQueue readyQueue;
    private final ProjectLogger logger;

    public Scheduler(BlockingQueue<Job> inbox, ReadyQueue readyQueue, ProjectLogger logger) {
        super("scheduler");
        this.inbox = inbox;
        this.readyQueue = readyQueue;
        this.logger = logger;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Job job = inbox.take();          // หลับรอ ไม่กิน CPU
                if (job == POISON) {
                    break;
                }
                readyQueue.add(job);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            readyQueue.close();                  // ปลุก Worker ให้รู้ว่าจบแล้ว
            logger.systemEvent("scheduler หยุด: ไม่มีงานเข้ามาอีก");
        }
    }
}

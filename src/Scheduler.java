import java.util.concurrent.BlockingQueue;

/**
 * รับงานจาก JobGenerator แล้วจัดเข้า Ready Queue
 *
 * - รับงานผ่าน BlockingQueue (take() พัก Thread ไม่กิน CPU)
 * - ใส่งานลง ReadyQueue ซึ่งจัดลำดับตามนโยบาย FCFS/Priority
 * - เมื่อได้ poison pill (JobGenerator.END) แปลว่าไม่มีงานเข้าอีก จึงจบ run() เอง
 *   (Main จะ close ReadyQueue หลังจาก Scheduler จบ)
 */
public class Scheduler extends Thread {

    private final BlockingQueue<Job> intake;
    private final ReadyQueue readyQueue;
    private final ProjectLogger logger;

    public Scheduler(BlockingQueue<Job> intake, ReadyQueue readyQueue, ProjectLogger logger) {
        super("scheduler");
        this.intake = intake;
        this.readyQueue = readyQueue;
        this.logger = logger;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Job job = intake.take();
                if (job == JobGenerator.END) {
                    break;
                }
                readyQueue.add(job);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

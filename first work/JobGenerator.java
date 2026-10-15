import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * ปล่อยงานเข้าสู่ระบบตามเวลา arrivalMs แล้วส่งให้ Scheduler ผ่าน BlockingQueue
 * เมื่อปล่อยครบจะส่ง poison pill (Scheduler.POISON) เพื่อบอกว่าไม่มีงานเข้ามาอีก
 */
public class JobGenerator extends Thread {

    private final List<Job> jobs;
    private final BlockingQueue<Job> inbox;
    private final ProjectLogger logger;

    public JobGenerator(List<Job> jobs, BlockingQueue<Job> inbox, ProjectLogger logger) {
        super("generator");
        // ไฟล์ไม่ได้เรียงตามเวลา จึงทำสำเนาแล้วเรียงตาม arrivalMs (เท่ากันใช้ลำดับในไฟล์)
        List<Job> sorted = new ArrayList<>(jobs);
        sorted.sort(Comparator.comparingLong((Job j) -> j.arrivalMs)
                .thenComparingInt(j -> j.sequence));
        this.jobs = sorted;
        this.inbox = inbox;
        this.logger = logger;
    }

    @Override
    public void run() {
        try {
            for (Job job : jobs) {
                long delay = job.arrivalMs - logger.now();
                if (delay > 0) {
                    Thread.sleep(delay);
                }
                job.arrivedMs = logger.now();   // เวลาเข้าระบบจริง
                logger.jobArrived(job);
                inbox.put(job);
            }
            inbox.put(Scheduler.POISON);        // บอกว่าปล่อยครบแล้ว
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

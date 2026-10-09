import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * ปล่อยงานเข้าสู่ระบบตามเวลา arrivalMs ของแต่ละ Job
 *
 * - เรียงงานตาม arrivalMs (เท่ากันใช้ sequence) เพราะไฟล์ไม่ได้เรียงตามเวลา
 * - รอจนถึงเวลาด้วย Thread.sleep (ไม่ busy wait) โดยคำนวณจากนาฬิกา logger.now()
 *   ทุกรอบ เพื่อไม่ให้ความคลาดเคลื่อนสะสม
 * - บันทึกเวลาเข้าระบบจริงลง job.enteredMs ก่อนส่งต่อ แล้วส่งให้ Scheduler ผ่าน BlockingQueue
 *   (ไม่ใส่ ReadyQueue โดยตรง)
 * - เมื่อปล่อยครบ ส่ง poison pill (END) เป็นสัญญาณให้ Scheduler หยุด
 *   ส่งใน finally เพื่อให้ Scheduler ไม่ค้างแม้ Generator จบผิดปกติ
 */
public class JobGenerator extends Thread {

    /** poison pill: งานสมมติที่บอกว่าไม่มีงานเข้ามาอีกแล้ว เทียบด้วย == */
    public static final Job END = new Job("__END__", 0, Integer.MAX_VALUE, 0, ResourceType.NONE, 0, -1);

    private final List<Job> jobs;
    private final BlockingQueue<Job> intake;
    private final ProjectLogger logger;

    public JobGenerator(List<Job> jobs, BlockingQueue<Job> intake, ProjectLogger logger) {
        super("generator");
        this.jobs = new ArrayList<>(jobs);
        this.jobs.sort(Comparator.<Job>comparingLong(j -> j.arrivalMs)
                .thenComparingInt(j -> j.sequence));
        this.intake = intake;
        this.logger = logger;
    }

    @Override
    public void run() {
        try {
            for (Job job : jobs) {
                long wait = job.arrivalMs - logger.now();
                if (wait > 0) {
                    Thread.sleep(wait);
                }
                job.enteredMs = logger.now();
                logger.jobArrived(job);
                intake.add(job); // คิวไม่จำกัดขนาด จึงไม่บล็อก
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            intake.add(END);
        }
    }
}

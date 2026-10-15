import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * จุดเริ่มต้นของโปรแกรม
 *
 * ===== ไฟล์นี้เป็นโครงเปล่า นักศึกษาต้องเขียนเอง =====
 *
 * ส่วนที่เขียนไว้ให้แล้วคือการรับค่า การโหลด workload และการแสดง error
 * ซึ่งไม่ใช่สิ่งที่โครงงานนี้วัด ส่วนที่เหลือเป็น TODO ทั้งหมด
 *
 * วิธีรัน:
 *   java Main jobs_standard.csv priority 3 1 2
 */
public class Main {

    public static void main(String[] args) {
        // ---------- 1. รับค่าจาก command line ----------
        Config config;
        try {
            config = Config.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("ผิดพลาด: " + e.getMessage());
            System.err.println();
            System.err.println(Config.USAGE);
            System.exit(1);
            return;
        }

        // ---------- 2. เริ่มจับเวลาและโหลด workload ----------
        ProjectLogger logger = new ProjectLogger();
        List<Job> jobs;
        try {
            jobs = WorkloadLoader.load(config.workloadPath);
        } catch (WorkloadFormatException e) {
            System.err.println("ไฟล์ workload ผิดรูปแบบ — " + e.getMessage());
            System.exit(1);
            return;
        } catch (java.io.IOException e) {
            System.err.println("เปิดไฟล์ \"" + config.workloadPath + "\" ไม่ได้");
            System.err.println("ตรวจว่าไฟล์มีอยู่จริงและ path ถูกต้อง (สั่ง java จากโฟลเดอร์ใด)");
            System.exit(1);
            return;
        }
        logger.systemStart(config);
        logger.systemEvent("โหลดงานได้ " + jobs.size() + " ชิ้น");

        // ---------- 3. สร้างส่วนประกอบของระบบ ----------
        ResourceManager resources = new ResourceManager(config.printerPermits, config.databasePermits);
        ReadyQueue readyQueue = new ReadyQueue(config.policy);
        Statistics statistics = new Statistics();
        BlockingQueue<Job> inbox = new LinkedBlockingQueue<>();

        // ---------- 4. สร้างและเริ่ม Thread ----------
        // start ฝั่งผู้บริโภคก่อน (Worker -> Scheduler -> Monitor) แล้วค่อย start JobGenerator
        // ตัวสุดท้าย เพื่อไม่ให้พลาดงานแรก; ความถูกต้องไม่ขึ้นกับลำดับ
        // เพราะใช้ blocking queue + poison pill
        List<Worker> workers = new ArrayList<>();
        for (int i = 1; i <= config.workers; i++) {
            Worker w = new Worker("worker-" + i, readyQueue, resources, statistics, logger);
            workers.add(w);
            w.start();
        }
        Scheduler scheduler = new Scheduler(inbox, readyQueue, logger);
        Monitor monitor = new Monitor(readyQueue, resources, statistics, logger);
        JobGenerator generator = new JobGenerator(jobs, inbox, logger);
        scheduler.start();
        monitor.start();
        generator.start();

        // ---------- 5-6. รอจนงานเสร็จครบ แล้วหยุดทุก Thread ----------
        // generator ส่ง poison -> scheduler ปิด ReadyQueue -> worker ทำงานที่ค้างจนหมด
        // แล้ว take() ได้ null จึงหยุดเอง ดังนั้น join worker = งานเสร็จครบจริง
        try {
            generator.join();
            scheduler.join();
            for (Worker w : workers) {
                w.join();
            }
            monitor.interrupt();
            monitor.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("main ถูก interrupt ก่อนงานเสร็จ");
        }

        // ---------- 7. สรุปผล ----------
        long makespanMs = statistics.lastFinishMs();
        statistics.printSummary(jobs, makespanMs);
        logger.systemStop(statistics.completedCount(), jobs.size());
    }
}

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
        BlockingQueue<Job> intake = new LinkedBlockingQueue<>();

        // ---------- 4. สร้างและเริ่ม Thread ----------
        // ลำดับ start: ฝั่งผู้บริโภค (Worker, Scheduler, Monitor) ก่อน แล้ว JobGenerator เป็นตัวสุดท้าย
        // ความถูกต้องไม่ขึ้นกับลำดับ เพราะส่งงานผ่านคิวที่บล็อกได้ แต่เริ่ม Generator ท้ายสุด
        // ทำให้ผู้รับพร้อมก่อนงานแรกเข้า และงานแรก (arrival=0) ไม่ถูกหน่วงโดยการสร้าง Thread อื่น
        List<Worker> workers = new ArrayList<>();
        for (int i = 1; i <= config.workers; i++) {
            Worker w = new Worker("worker-" + i, readyQueue, resources, statistics, logger);
            workers.add(w);
            w.start();
        }
        Scheduler scheduler = new Scheduler(intake, readyQueue, logger);
        scheduler.start();
        Monitor monitor = new Monitor(readyQueue, resources, statistics, logger);
        monitor.start();
        JobGenerator generator = new JobGenerator(jobs, intake, logger);
        generator.start();

        // ---------- 5-6. รอจนงานเสร็จครบ แล้วหยุดทุก Thread ตามลำดับ ----------
        // ไม่มีการเดาเวลา ใช้ห่วงโซ่สัญญาณ:
        //   Generator ปล่อยครบ -> ส่ง poison pill -> Scheduler จบ
        //   -> close ReadyQueue -> Worker ทำงานที่ค้างจนหมดแล้วได้ null และจบเอง
        //   -> join Worker ครบ = งานเสร็จครบ -> interrupt Monitor
        try {
            generator.join();
            scheduler.join();
            readyQueue.close();
            for (Worker w : workers) {
                w.join();
            }
            monitor.interrupt();
            monitor.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("main ถูก interrupt ก่อนระบบหยุดสมบูรณ์");
        }

        // ---------- 7. สรุปผล ----------
        // makespan = เวลาที่งานชิ้นสุดท้ายเสร็จ (completeMs ใหญ่สุด ฐานเวลาเดียวกับ logger.now())
        long makespanMs = 0;
        for (Job j : jobs) {
            if (j.isCompleted() && j.completeMs > makespanMs) {
                makespanMs = j.completeMs;
            }
        }
        statistics.printSummary(jobs, makespanMs);
        logger.systemStop(statistics.completedCount(), jobs.size());
    }
}

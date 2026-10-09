/**
 * Thread ที่ดึงงานจาก Ready Queue ไปทำจนเสร็จ
 *
 * ลำดับงานหนึ่งชิ้น (หัวข้อ 6): รับงาน -> sleep(workMs) -> [รอ+acquire resource]
 * -> sleep(resourceMs) -> release -> จบ
 *
 * การหยุด: take() คืน null เมื่อคิวว่างและถูกปิด Worker จึงหยุดเองหลังทำงานที่เหลือครบ
 * การคืน permit: release อยู่ใน finally ของบล็อกที่ acquire สำเร็จแล้วเท่านั้น
 *   - ถูก interrupt/เกิด exception ระหว่างถือ -> finally ยังคืน permit
 *   - ถูก interrupt ระหว่างรอ acquire -> ยังไม่ได้ permit จึงไม่ release (ไม่คืนเกิน)
 */
public class Worker extends Thread {

    private final ReadyQueue readyQueue;
    private final ResourceManager resources;
    private final Statistics statistics;
    private final ProjectLogger logger;

    public Worker(String name, ReadyQueue readyQueue, ResourceManager resources,
                  Statistics statistics, ProjectLogger logger) {
        super(name);
        this.readyQueue = readyQueue;
        this.resources = resources;
        this.statistics = statistics;
        this.logger = logger;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Job job = readyQueue.take();
                if (job == null) {
                    break; // ไม่มีงานเหลือและจะไม่มีงานเข้ามาอีก
                }
                boolean done = false;
                try {
                    processJob(job);
                    done = true;
                } catch (RuntimeException e) {
                    logger.systemEvent("งาน " + job.id + " ล้มเหลว: " + e);
                } finally {
                    if (done) {
                        readyQueue.jobFinished();
                    } else {
                        readyQueue.jobAborted(); // รวมกรณี InterruptedException ที่ลอยออกไป
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** ทำงานหนึ่งชิ้นให้จบตามลำดับขั้น */
    private void processJob(Job job) throws InterruptedException {
        // 1. เริ่มงาน
        job.startMs = logger.now();
        logger.jobStarted(job);

        // 2. งานหลัก
        Thread.sleep(job.workMs);
        job.workEndMs = logger.now();
        logger.workFinished(job);

        if (job.resource == ResourceType.NONE) {
            job.acquiredMs = job.workEndMs;
            job.completeMs = job.workEndMs;
        } else {
            // 3. รอและขอ resource (เวลาเริ่มรอ = workEndMs)
            logger.resourceWaitStarted(job);
            resources.acquire(job.resource);
            // ถึงตรงนี้ถือ permit แล้ว ต้อง release เสมอ
            try {
                job.acquiredMs = logger.now();
                logger.resourceAcquired(job, job.acquiredMs - job.workEndMs);

                // 4. ถือครอง resource
                Thread.sleep(job.resourceMs);
            } finally {
                // 5. คืน resource
                resources.release(job.resource);
                job.completeMs = logger.now();
                logger.resourceReleased(job);
            }
        }

        statistics.recordCompletion(job);
        logger.jobCompleted(job);
    }
}

/**
 * Thread ที่ดึงงานจาก Ready Queue ไปทำจนเสร็จ
 * หยุดเองเมื่อ take() คืน null (คิวปิดและว่าง)
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
                    return;                      // ไม่มีงานเหลือแล้ว
                }
                boolean ok = false;
                try {
                    processJob(job);
                    ok = true;
                } finally {
                    // ลด running เสมอ แม้ถูก interrupt/exception; บันทึกเสร็จเฉพาะเมื่อสำเร็จ
                    readyQueue.finishRunning(job, ok ? statistics : null);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** ทำงานหนึ่งชิ้นตาม 5 ขั้นตอน */
    private void processJob(Job job) throws InterruptedException {
        job.startMs = logger.now();
        logger.jobStarted(job);

        Thread.sleep(job.workMs);
        logger.workFinished(job);

        if (job.resource != ResourceType.NONE) {
            job.resourceWaitStartMs = logger.now();
            logger.resourceWaitStarted(job);
            resources.acquire(job.resource);     // ถ้า throw = ยังไม่ได้ permit จึงไม่ต้องคืน
            // ถึงตรงนี้ถือ permit แล้ว: ต้อง release ใน finally เสมอ
            try {
                long waited = logger.now() - job.resourceWaitStartMs;
                job.resourceWaitMs = waited;
                logger.resourceAcquired(job, waited);
                Thread.sleep(job.resourceMs);
            } finally {
                resources.release(job.resource);
                logger.resourceReleased(job);
            }
        }

        job.finishMs = logger.now();
        logger.jobCompleted(job);
    }
}

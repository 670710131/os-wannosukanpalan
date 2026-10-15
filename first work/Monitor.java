/**
 * รายงานสถานะระบบทุก ~1000 ms
 * ready/running/completed อ่านจาก ReadyQueue.snapshot() ภายใน lock เดียว
 */
public class Monitor extends Thread {

    private final ReadyQueue readyQueue;
    private final ResourceManager resources;
    private final Statistics statistics;
    private final ProjectLogger logger;

    public Monitor(ReadyQueue readyQueue, ResourceManager resources,
                   Statistics statistics, ProjectLogger logger) {
        super("monitor");
        this.readyQueue = readyQueue;
        this.resources = resources;
        this.statistics = statistics;
        this.logger = logger;
    }

    @Override
    public void run() {
        try {
            while (!isInterrupted()) {
                ReadyQueue.Snapshot s = readyQueue.snapshot(statistics);
                logger.monitor(s.ready, s.running, s.completed, resources.status());
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            // ได้รับสัญญาณให้หยุด
        }
    }
}

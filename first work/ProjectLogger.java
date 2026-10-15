import java.io.PrintStream;

/**
 * บันทึกเหตุการณ์ของระบบด้วยรูปแบบที่ตายตัว
 *
 * ไฟล์นี้เป็นโค้ดตั้งต้นที่อาจารย์แจก ไม่ต้องแก้ และไม่มีคะแนนผูกกับการจัดรูปแบบ log
 *
 * เหตุผลที่แจกให้:
 *   1. log ของทุกกลุ่มจะมีหน้าตาเหมือนกัน ทำให้ผู้ตรวจอ่านได้เร็วและเทียบกันได้
 *   2. การเขียนจากหลาย Thread ถูกป้องกันให้แล้ว บรรทัดจะไม่ซ้อนกัน
 *   3. now() ให้เวลาฐานเดียวกับที่ปรากฏใน log นักศึกษาจึงควรใช้ตัวนี้วัดผลด้วย
 *      เพื่อให้ค่าที่รายงานกับ log ตรวจสอบย้อนกลับกันได้
 *
 * รูปแบบบรรทัด:
 *   [    1234 ms] [worker-2  ] JOB_STARTED    job=J04 priority=1
 *
 * การเก็บ log ลงไฟล์ ให้ redirect ตอนรัน ไม่ต้องเขียนโค้ดเพิ่ม:
 *   java Main jobs_standard.csv priority 3 1 2 > logs/standard_priority_w3.log
 */
public final class ProjectLogger {

    private final long startNanos;
    private final PrintStream out;
    private final Object writeLock = new Object();

    /** สร้าง logger และเริ่มจับเวลาทันที ควรสร้างเพียงตัวเดียวต่อการรันหนึ่งครั้ง */
    public ProjectLogger() {
        this(System.out);
    }

    public ProjectLogger(PrintStream out) {
        this.out = out;
        this.startNanos = System.nanoTime();
    }

    /**
     * เวลาปัจจุบันเป็นมิลลิวินาที นับจากตอนที่ logger ถูกสร้าง
     *
     * ใช้ตัวนี้เป็นนาฬิกาเดียวของทั้งระบบ ทั้งการบันทึก log และการวัดผล
     * อย่าใช้ System.currentTimeMillis() ปนกับตัวนี้ เพราะคนละฐานเวลา
     */
    public long now() {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    // ---------- เหตุการณ์ระดับระบบ ----------

    public void systemStart(Config config) {
        write("SYSTEM_START", config.describe());
    }

    public void systemEvent(String detail) {
        write("SYSTEM", detail);
    }

    public void systemStop(int completedJobs, int totalJobs) {
        write("SYSTEM_STOP", "completed=" + completedJobs + "/" + totalJobs);
    }

    // ---------- เหตุการณ์ระดับ Job ----------

    /** งานเข้าสู่ระบบตามเวลา arrivalMs ของมัน */
    public void jobArrived(Job job) {
        write("JOB_ARRIVED", detailOf(job));
    }

    /** Worker รับงานไปเริ่มทำ */
    public void jobStarted(Job job) {
        write("JOB_STARTED", "job=" + job.id + " priority=" + job.priority);
    }

    /** งานหลักเสร็จแล้ว ขั้นถัดไปคือขอใช้ทรัพยากร (ถ้ามี) */
    public void workFinished(Job job) {
        write("WORK_FINISHED", "job=" + job.id);
    }

    /** เริ่มรอสิทธิ์ใช้ทรัพยากร */
    public void resourceWaitStarted(Job job) {
        write("RESOURCE_WAIT", "job=" + job.id + " resource=" + job.resource);
    }

    /** ได้สิทธิ์ใช้ทรัพยากรแล้ว waitedMs คือเวลาที่รออยู่ */
    public void resourceAcquired(Job job, long waitedMs) {
        write("RESOURCE_ACQUIRED",
                "job=" + job.id + " resource=" + job.resource + " waited=" + waitedMs + "ms");
    }

    /** คืนสิทธิ์ใช้ทรัพยากรแล้ว */
    public void resourceReleased(Job job) {
        write("RESOURCE_RELEASED", "job=" + job.id + " resource=" + job.resource);
    }

    /** งานเสร็จสมบูรณ์ */
    public void jobCompleted(Job job) {
        write("JOB_COMPLETED", "job=" + job.id);
    }

    // ---------- เหตุการณ์จาก Monitor ----------

    /**
     * รายงานสถานะระบบจาก Monitor Thread
     *
     * หมายเหตุ: logger เพียงพิมพ์ตัวเลขที่ได้รับมา การอ่านค่าเหล่านี้
     * ให้เป็น snapshot ที่ปลอดภัยขณะที่ Worker กำลังแก้ไขอยู่
     * ยังเป็นสิ่งที่นักศึกษาต้องออกแบบเอง
     */
    public void monitor(int ready, int running, int completed, String resourceStatus) {
        write("MONITOR", "ready=" + ready + " running=" + running
                + " completed=" + completed + " " + resourceStatus);
    }

    // ---------- ภายใน ----------

    private static String detailOf(Job job) {
        StringBuilder sb = new StringBuilder();
        sb.append("job=").append(job.id)
          .append(" priority=").append(job.priority)
          .append(" work=").append(job.workMs).append("ms");
        if (job.resource != ResourceType.NONE) {
            sb.append(" resource=").append(job.resource)
              .append('(').append(job.resourceMs).append("ms)");
        }
        return sb.toString();
    }

    private void write(String event, String detail) {
        String line = String.format("[%8d ms] [%-10s] %-18s %s",
                now(),
                shorten(Thread.currentThread().getName()),
                event,
                detail == null ? "" : detail);
        synchronized (writeLock) {
            out.println(line);
        }
    }

    private static String shorten(String name) {
        return name.length() <= 10 ? name : name.substring(0, 10);
    }
}

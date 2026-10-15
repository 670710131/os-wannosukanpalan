/**
 * ข้อมูลของงานหนึ่งชิ้น (ฟิลด์จาก CSV เป็น final, ฟิลด์วัดผลเป็น volatile)
 */
public class Job {

    public final String id;
    public final long arrivalMs;
    public final int priority;
    public final long workMs;
    public final ResourceType resource;
    public final long resourceMs;
    public final int sequence;

    // ---------- ฟิลด์วัดผล ----------
    // นาฬิกา: ProjectLogger.now() ตัวเดียว (ฐานเวลาเดียวกับ log)
    // ใครเขียน/ใครอ่าน:
    //   arrivedMs          เขียนโดย JobGenerator           -> อ่านโดย Worker/Statistics/Main
    //   startMs, finishMs  เขียนโดย Worker ที่ทำงานนี้       -> อ่านโดย Statistics/Main
    //   resourceWaitStartMs, resourceWaitMs เขียนโดย Worker  -> อ่านโดย Statistics
    // การป้องกัน: ประกาศ volatile เพื่อให้ค่าที่เขียนมองเห็นข้าม Thread
    //   (และแต่ละฟิลด์มี writer เพียง Thread เดียวต่อรอบ จึงไม่ต้อง lock เพิ่ม
    //    อีกทั้งการส่งต่อผ่าน BlockingQueue/ReadyQueue ก็สร้าง happens-before ให้อยู่แล้ว)
    // ค่า -1 = ยังไม่เกิดเหตุการณ์นั้น
    public volatile long arrivedMs = -1;
    public volatile long startMs = -1;
    public volatile long finishMs = -1;
    public volatile long resourceWaitStartMs = -1;
    public volatile long resourceWaitMs = 0;   // งานที่ไม่ใช้ resource คงเป็น 0

    public Job(String id, long arrivalMs, int priority, long workMs,
               ResourceType resource, long resourceMs, int sequence) {
        this.id = id;
        this.arrivalMs = arrivalMs;
        this.priority = priority;
        this.workMs = workMs;
        this.resource = resource;
        this.resourceMs = resourceMs;
        this.sequence = sequence;
    }

    /** Waiting Time = เวลาที่เริ่มทำ - เวลาที่เข้าระบบ */
    public long waitingMs() { return startMs - arrivedMs; }

    /** Turnaround Time = เวลาที่เสร็จ - เวลาที่เข้าระบบ */
    public long turnaroundMs() { return finishMs - arrivedMs; }

    @Override
    public String toString() {
        return String.format("%s(priority=%d, work=%dms, %s)",
                id, priority, workMs,
                resource == ResourceType.NONE ? "no resource"
                        : resource + " " + resourceMs + "ms");
    }
}

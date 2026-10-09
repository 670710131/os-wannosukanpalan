/**
 * ข้อมูลของงานหนึ่งชิ้น
 *
 * ฟิลด์ส่วนแรก (id ... sequence) มาจากไฟล์ workload CSV โดยตรง ถูกกำหนดครั้งเดียวตอนโหลด
 * จึงเป็น final และปลอดภัยเมื่อหลาย Thread อ่านพร้อมกัน
 *
 * ฟิลด์ส่วนที่สอง (ด้านล่าง) เป็นเวลาที่ใช้วัดผล ทุกค่าอ่านจาก ProjectLogger.now()
 * ซึ่งเป็นนาฬิกาฐานเดียวกับที่ปรากฏใน log
 */
public class Job {

    /** รหัสงาน เช่น J01 — ไม่ซ้ำกันภายในหนึ่งไฟล์ workload */
    public final String id;

    /** เวลาที่งานควรเข้าสู่ระบบ นับจากวินาทีที่โปรแกรมเริ่ม (มิลลิวินาที) */
    public final long arrivalMs;

    /** ระดับความสำคัญ โดย 1 คือสูงสุด ตัวเลขยิ่งมากยิ่งสำคัญน้อย */
    public final int priority;

    /** ระยะเวลาของงานหลัก ก่อนขอใช้ทรัพยากรร่วม (มิลลิวินาที) */
    public final long workMs;

    /** ทรัพยากรร่วมที่ต้องใช้ หรือ NONE ถ้าไม่ต้องใช้ */
    public final ResourceType resource;

    /** ระยะเวลาที่ถือครองทรัพยากร (มิลลิวินาที) เป็น 0 เสมอเมื่อ resource เป็น NONE */
    public final long resourceMs;

    /** ลำดับที่งานนี้ปรากฏในไฟล์ workload เริ่มจาก 0 ใช้เป็นตัวตัดสินสุดท้ายใน tie-break */
    public final int sequence;

    // ---------------------------------------------------------------------
    // ฟิลด์วัดผล
    //
    // ใครเขียน / ใครอ่าน:
    //   enteredMs               เขียนโดย JobGenerator            อ่านโดย Worker / Statistics
    //   startMs, workEndMs,
    //   acquiredMs, completeMs  เขียนโดย Worker (ตัวเดียวที่ถืองานนั้น)
    //                           อ่านโดย Statistics, Monitor-ผ่านสรุปผล, Main
    //
    // การป้องกัน:
    //   - ประกาศ volatile เพื่อให้การเขียนของ Thread หนึ่งมองเห็นได้ใน Thread อื่นแน่นอน
    //   - นอกจากนี้การส่งต่องานระหว่าง Thread ผ่าน BlockingQueue / ReadyQueue (lock)
    //     และ Thread.join() ใน Main ก็สร้าง happens-before อยู่แล้ว
    //   - ไม่ต้องใช้ lock เพิ่ม เพราะแต่ละฟิลด์มีผู้เขียนคนเดียวและเขียนครั้งเดียวต่องาน
    //
    // เส้นเวลาของงานหนึ่งชิ้น (ทุกค่าเป็น ms จาก ProjectLogger.now()):
    //   enteredMs -> startMs -> workEndMs -> acquiredMs -> completeMs
    // งานที่ไม่ใช้ resource กำหนด acquiredMs = completeMs = workEndMs
    // ทำให้ผลต่างแต่ละช่วงต่อกันเป็นแถวเดียว (telescoping) และสมการตรวจสอบเป็นจริงเป๊ะ:
    //   Turnaround = Waiting + (work จริง) + Resource Wait + (ถือ resource จริง)
    // ---------------------------------------------------------------------

    private static final long UNSET = -1L;

    /** เวลาที่งานเข้าสู่ระบบจริง (JobGenerator ปล่อยงาน) */
    public volatile long enteredMs = UNSET;

    /** เวลาที่ Worker เริ่มทำงานนี้ */
    public volatile long startMs = UNSET;

    /** เวลาที่งานหลักเสร็จ = เวลาที่เริ่มรอ resource */
    public volatile long workEndMs = UNSET;

    /** เวลาที่ได้ resource (ถ้าไม่ใช้ resource เท่ากับ workEndMs) */
    public volatile long acquiredMs = UNSET;

    /** เวลาที่งานเสร็จสมบูรณ์ (หลังคืน resource ถ้ามี) */
    public volatile long completeMs = UNSET;

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

    /** true เมื่อ Worker ทำงานนี้จบครบทุกขั้นแล้ว */
    public boolean isCompleted() {
        return completeMs != UNSET;
    }

    // ---------- ค่าที่คำนวณจากเวลาที่บันทึกไว้ (เรียกได้เมื่องานเสร็จแล้วเท่านั้น) ----------

    /** เวลารอในคิวก่อนถูกหยิบ */
    public long waitingMs() {
        return startMs - enteredMs;
    }

    /** เวลาทำงานหลักที่เกิดขึ้นจริง (ยาวกว่า workMs เล็กน้อยเพราะ sleep ไม่เป๊ะ) */
    public long actualWorkMs() {
        return workEndMs - startMs;
    }

    /** เวลารอ resource รวม (0 เมื่อไม่ใช้ resource) */
    public long resourceWaitMs() {
        return acquiredMs - workEndMs;
    }

    /** เวลาถือ resource ที่เกิดขึ้นจริง (0 เมื่อไม่ใช้ resource) */
    public long actualResourceHoldMs() {
        return completeMs - acquiredMs;
    }

    /** เวลาตั้งแต่เข้าระบบจนเสร็จ */
    public long turnaroundMs() {
        return completeMs - enteredMs;
    }

    @Override
    public String toString() {
        return String.format("%s(priority=%d, work=%dms, %s)",
                id, priority, workMs,
                resource == ResourceType.NONE ? "no resource"
                        : resource + " " + resourceMs + "ms");
    }
}

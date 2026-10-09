import java.util.concurrent.Semaphore;

/**
 * ควบคุมสิทธิ์การใช้ทรัพยากรร่วมของทั้งระบบ
 *
 * - ใช้ Semaphore แบบ fair = true สำหรับ PRINTER และ DATABASE
 * - Worker ทุกตัวใช้ object เดียวกันนี้ (สร้างครั้งเดียวใน Main)
 * - คลาสนี้ "ไม่" รับผิดชอบการคืน permit เมื่อเกิด exception
 *   ผู้เรียกต้องจับคู่ acquire/release ด้วย try/finally (ดู Worker.processJob)
 *
 * ทำไมต้อง fair = true:
 *   fair คือรับประกันลำดับ FIFO ของ Thread ที่รอ ทำให้งานที่มาขอก่อนได้ก่อนเสมอ
 *   ถ้าเป็น false (non-fair) Thread ที่เพิ่งมาถึงอาจ "แทรกคิว" แย่ง permit ที่เพิ่งถูกคืน
 *   ไปก่อนคนที่รอมานาน ทำให้งานบางชิ้นอาจรอนานผิดปกติ (starvation) และเวลารอ resource
 *   ของแต่ละการรันแกว่งมากขึ้น เทียบผลระหว่างกลุ่มไม่ได้ (non-fair มักมี throughput
 *   สูงกว่าเล็กน้อยเพราะลด context switch แต่แลกกับความยุติธรรม)
 */
public class ResourceManager {

    private final Semaphore printer;
    private final Semaphore database;
    private final int printerTotal;
    private final int databaseTotal;

    public ResourceManager(int printerPermits, int databasePermits) {
        this.printerTotal = printerPermits;
        this.databaseTotal = databasePermits;
        this.printer = new Semaphore(printerPermits, true);   // fair = true
        this.database = new Semaphore(databasePermits, true); // fair = true
    }

    /** ขอสิทธิ์ใช้ทรัพยากร จะรอจนกว่าจะได้ ถ้าถูก interrupt ขณะรอจะโยน InterruptedException
     *  และถือว่า "ไม่ได้" permit (ผู้เรียกต้องไม่ release) */
    public void acquire(ResourceType type) throws InterruptedException {
        Semaphore s = semaphoreOf(type);
        if (s != null) {
            s.acquire();
        }
    }

    /** คืนสิทธิ์ใช้ทรัพยากร */
    public void release(ResourceType type) {
        Semaphore s = semaphoreOf(type);
        if (s != null) {
            s.release();
        }
    }

    /** ข้อความสถานะ รูปแบบ "printer=ที่ใช้อยู่/ทั้งหมด database=ที่ใช้อยู่/ทั้งหมด" */
    public String status() {
        int printerInUse = printerTotal - printer.availablePermits();
        int databaseInUse = databaseTotal - database.availablePermits();
        return "printer=" + printerInUse + "/" + printerTotal
                + " database=" + databaseInUse + "/" + databaseTotal;
    }

    private Semaphore semaphoreOf(ResourceType type) {
        switch (type) {
            case PRINTER:  return printer;
            case DATABASE: return database;
            default:       return null; // NONE
        }
    }
}

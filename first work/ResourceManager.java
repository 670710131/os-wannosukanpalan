import java.util.concurrent.Semaphore;

/**
 * ควบคุมสิทธิ์ใช้ PRINTER / DATABASE ด้วย Semaphore แบบ fair = true
 * (fair ทำให้ผู้รอได้ permit ตามลำดับที่มาขอ ไม่เกิด starvation)
 */
public class ResourceManager {

    private final Semaphore printer;
    private final Semaphore database;
    private final int printerTotal;
    private final int databaseTotal;

    public ResourceManager(int printerPermits, int databasePermits) {
        this.printerTotal = printerPermits;
        this.databaseTotal = databasePermits;
        this.printer = new Semaphore(printerPermits, true);
        this.database = new Semaphore(databasePermits, true);
    }

    private Semaphore of(ResourceType type) {
        switch (type) {
            case PRINTER:  return printer;
            case DATABASE: return database;
            default:       return null;   // NONE
        }
    }

    /** ขอสิทธิ์ใช้ทรัพยากร จะรอจนกว่าจะได้ (NONE ไม่ทำอะไร) */
    public void acquire(ResourceType type) throws InterruptedException {
        Semaphore s = of(type);
        if (s != null) {
            s.acquire();
        }
    }

    /** คืนสิทธิ์ใช้ทรัพยากร */
    public void release(ResourceType type) {
        Semaphore s = of(type);
        if (s != null) {
            s.release();
        }
    }

    /** รูปแบบ "printer=ใช้อยู่/ทั้งหมด database=ใช้อยู่/ทั้งหมด" */
    public String status() {
        return "printer=" + (printerTotal - printer.availablePermits()) + "/" + printerTotal
                + " database=" + (databaseTotal - database.availablePermits()) + "/" + databaseTotal;
    }
}

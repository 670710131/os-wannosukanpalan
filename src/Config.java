/**
 * ค่าที่รับมาจาก command line
 *
 * ไฟล์นี้เป็นโค้ดตั้งต้นที่อาจารย์แจก ไม่ต้องแก้
 * เหตุผลที่แจกให้: ผู้ตรวจต้องสั่งรันทุกกลุ่มด้วยคำสั่งรูปแบบเดียวกัน
 * และต้องรู้ชัดว่ากำลังรันด้วยค่าใด จึงไม่มีค่า default ซ่อนอยู่เลย
 *
 * รูปแบบการใช้งาน:
 *   java Main jobs_standard.csv priority 3 1 2
 */
public final class Config {

    public static final String USAGE =
            "Usage: java Main <workload.csv> <fcfs|priority> <workers> <printerPermits> <databasePermits>\n"
          + "  workload.csv      test workload file\n"
          + "  fcfs | priority   job scheduling policy\n"
          + "  workers           number of Worker Threads (1 or more)\n"
          + "  printerPermits    number of concurrent PRINTER permits (1 or more)\n"
          + "  databasePermits   number of concurrent DATABASE permits (1 or more)\n"
          + "\n"
          + "Example: java Main jobs_standard.csv priority 3 1 2";

    /** นโยบายการจัดลำดับงาน */
    public enum Policy {
        FCFS,
        PRIORITY
    }

    public final String workloadPath;
    public final Policy policy;
    public final int workers;
    public final int printerPermits;
    public final int databasePermits;

    private Config(String workloadPath, Policy policy, int workers,
                   int printerPermits, int databasePermits) {
        this.workloadPath = workloadPath;
        this.policy = policy;
        this.workers = workers;
        this.printerPermits = printerPermits;
        this.databasePermits = databasePermits;
    }

    /**
     * แปลง argument จาก main() เป็น Config
     *
     * @throws IllegalArgumentException เมื่อจำนวนหรือค่าของ argument ไม่ถูกต้อง
     *         ข้อความของ exception เหมาะกับการแสดงต่อผู้ใช้โดยตรง
     */
    public static Config parse(String[] args) {
        if (args.length != 5) {
            throw new IllegalArgumentException(
                    "Exactly 5 arguments are required, but received " + args.length);
        }

        String workloadPath = args[0].trim();
        if (workloadPath.isEmpty()) {
            throw new IllegalArgumentException("Workload file name is empty");
        }

        Policy policy;
        String policyText = args[1].trim().toLowerCase();
        if (policyText.equals("fcfs")) {
            policy = Policy.FCFS;
        } else if (policyText.equals("priority")) {
            policy = Policy.PRIORITY;
        } else {
            throw new IllegalArgumentException(
                    "Policy must be either fcfs or priority, but found \"" + args[1].trim() + "\"");
        }

        int workers = parseAtLeastOne(args[2], "workers");
        int printerPermits = parseAtLeastOne(args[3], "printerPermits");
        int databasePermits = parseAtLeastOne(args[4], "databasePermits");

        return new Config(workloadPath, policy, workers, printerPermits, databasePermits);
    }

    private static int parseAtLeastOne(String text, String name) {
        int value;
        try {
            value = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    name + " must be an integer, but found \"" + text.trim() + "\"");
        }
        if (value < 1) {
            throw new IllegalArgumentException(
                    name + " must be 1 or greater, but found " + value);
        }
        return value;
    }

    /** ข้อความบรรทัดเดียวสำหรับบันทึกลง log ว่ารันด้วยค่าใด */
    public String describe() {
        return String.format("workload=%s policy=%s workers=%d printer=%d database=%d",
                workloadPath, policy.name().toLowerCase(), workers, printerPermits, databasePermits);
    }
}
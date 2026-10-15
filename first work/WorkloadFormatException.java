/**
 * เกิดขึ้นเมื่อไฟล์ workload CSV มีรูปแบบหรือค่าที่ไม่ถูกต้อง
 *
 * ข้อความของ exception นี้ออกแบบมาให้แสดงต่อผู้ใช้ได้โดยตรง
 * โดยระบุหมายเลขบรรทัดที่มีปัญหาเสมอ
 *
 * ไฟล์นี้เป็นโค้ดตั้งต้นที่อาจารย์แจก ไม่ต้องแก้
 */
public class WorkloadFormatException extends Exception {

    private static final long serialVersionUID = 1L;

    /** หมายเลขบรรทัดในไฟล์ เริ่มนับที่ 1 หรือ 0 ถ้าปัญหาไม่ผูกกับบรรทัดใด */
    private final int lineNumber;

    public WorkloadFormatException(int lineNumber, String reason) {
        super(lineNumber > 0
                ? "บรรทัดที่ " + lineNumber + ": " + reason
                : reason);
        this.lineNumber = lineNumber;
    }

    public int getLineNumber() {
        return lineNumber;
    }
}

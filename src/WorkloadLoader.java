import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * อ่านไฟล์ workload CSV และตรวจความถูกต้องของข้อมูล
 *
 * ไฟล์นี้เป็นโค้ดตั้งต้นที่อาจารย์แจก ไม่ต้องแก้ และไม่มีคะแนนส่วนใดผูกกับ
 * การเขียนตัวอ่าน CSV หรือการตรวจไฟล์ที่รูปแบบผิด
 *
 * รูปแบบไฟล์ที่รองรับ:
 *   id,arrivalMs,priority,workMs,resource,resourceMs
 *   J01,0,5,2500,NONE,0
 *
 * - บรรทัดแรกเป็นหัวตารางหรือไม่มีก็ได้ ตัวอ่านตรวจจับให้เอง
 * - บรรทัดว่างถูกข้าม
 * - รองรับไฟล์ที่ลงท้ายบรรทัดแบบ LF และ CRLF และตัดช่องว่างหน้า/หลังทุกช่อง
 */
public final class WorkloadLoader {

    private static final int COLUMN_COUNT = 6;

    private WorkloadLoader() {
        // ห้ามสร้าง object ของคลาสนี้
    }

    /**
     * อ่านไฟล์ workload ทั้งไฟล์
     *
     * @param path เส้นทางไฟล์ CSV
     * @return รายการ Job เรียงตามลำดับที่ปรากฏในไฟล์ (ไม่ได้เรียงตาม arrivalMs)
     *         รายการที่คืนกลับแก้ไขไม่ได้
     * @throws IOException              เมื่อเปิดไฟล์ไม่ได้
     * @throws WorkloadFormatException  เมื่อเนื้อหาในไฟล์ไม่ถูกต้อง
     */
    public static List<Job> load(Path path) throws IOException, WorkloadFormatException {
        List<Job> jobs = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        int lineNumber = 0;
        boolean firstDataLine = true;

        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String rawLine;
            while ((rawLine = reader.readLine()) != null) {
                lineNumber++;

                // ตัด BOM ที่โปรแกรมบางตัวใส่ไว้หน้าไฟล์ UTF-8
                if (lineNumber == 1 && !rawLine.isEmpty() && rawLine.charAt(0) == '\uFEFF') {
                    rawLine = rawLine.substring(1);
                }

                String line = rawLine.trim();
                if (line.isEmpty()) {
                    continue;
                }

                // ข้ามหัวตาราง ถ้ามี — ตรวจจากคอลัมน์แรกที่เขียนว่า id
                if (firstDataLine && line.toLowerCase().startsWith("id,")) {
                    firstDataLine = false;
                    continue;
                }
                firstDataLine = false;

                Job job = parseLine(line, lineNumber, jobs.size());
                if (!seenIds.add(job.id)) {
                    throw new WorkloadFormatException(lineNumber, "รหัสงาน \"" + job.id + "\" ซ้ำกับบรรทัดก่อนหน้า");
                }
                jobs.add(job);
            }
        }

        if (jobs.isEmpty()) {
            throw new WorkloadFormatException(0, "ไฟล์ " + path + " ไม่มีข้อมูลงานเลย");
        }
        return Collections.unmodifiableList(jobs);
    }

    /** ทางลัดสำหรับเรียกด้วยชื่อไฟล์เป็นข้อความ */
    public static List<Job> load(String path) throws IOException, WorkloadFormatException {
        return load(Path.of(path));
    }

    // ------------------------------------------------------------------

    private static Job parseLine(String line, int lineNumber, int sequence)
            throws WorkloadFormatException {

        String[] parts = line.split(",", -1);
        if (parts.length != COLUMN_COUNT) {
            throw new WorkloadFormatException(lineNumber,
                    "ต้องมี " + COLUMN_COUNT + " คอลัมน์ แต่พบ " + parts.length + " คอลัมน์");
        }
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].trim();
        }

        String id = parts[0];
        if (id.isEmpty()) {
            throw new WorkloadFormatException(lineNumber, "คอลัมน์ id เป็นค่าว่าง");
        }

        long arrivalMs = parseNonNegativeLong(parts[1], "arrivalMs", lineNumber);
        int priority = parsePositiveInt(parts[2], "priority", lineNumber);
        long workMs = parseNonNegativeLong(parts[3], "workMs", lineNumber);

        ResourceType resource;
        try {
            resource = ResourceType.parse(parts[4]);
        } catch (IllegalArgumentException e) {
            throw new WorkloadFormatException(lineNumber, e.getMessage());
        }

        long resourceMs = parseNonNegativeLong(parts[5], "resourceMs", lineNumber);

        if (resource == ResourceType.NONE && resourceMs != 0) {
            throw new WorkloadFormatException(lineNumber,
                    "เมื่อ resource เป็น NONE ค่า resourceMs ต้องเป็น 0 แต่พบ " + resourceMs);
        }
        if (resource != ResourceType.NONE && resourceMs <= 0) {
            throw new WorkloadFormatException(lineNumber,
                    "เมื่อ resource เป็น " + resource + " ค่า resourceMs ต้องมากกว่า 0 แต่พบ " + resourceMs);
        }

        return new Job(id, arrivalMs, priority, workMs, resource, resourceMs, sequence);
    }

    private static long parseNonNegativeLong(String text, String column, int lineNumber)
            throws WorkloadFormatException {
        long value;
        try {
            value = Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new WorkloadFormatException(lineNumber,
                    "คอลัมน์ " + column + " ต้องเป็นจำนวนเต็ม แต่พบ \"" + text + "\"");
        }
        if (value < 0) {
            throw new WorkloadFormatException(lineNumber,
                    "คอลัมน์ " + column + " ต้องไม่ติดลบ แต่พบ " + value);
        }
        return value;
    }

    private static int parsePositiveInt(String text, String column, int lineNumber)
            throws WorkloadFormatException {
        int value;
        try {
            value = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new WorkloadFormatException(lineNumber,
                    "คอลัมน์ " + column + " ต้องเป็นจำนวนเต็ม แต่พบ \"" + text + "\"");
        }
        if (value < 1) {
            throw new WorkloadFormatException(lineNumber,
                    "คอลัมน์ " + column + " ต้องมีค่าตั้งแต่ 1 ขึ้นไป (1 คือสำคัญที่สุด) แต่พบ " + value);
        }
        return value;
    }
}

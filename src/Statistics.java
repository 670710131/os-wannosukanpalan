import java.util.List;

/**
 * รวบรวมและคำนวณค่าที่ใช้วัดผลของการรันหนึ่งครั้ง
 *
 * - recordCompletion เป็น synchronized: Worker หลายตัวอัปเดตผลรวมพร้อมกันได้อย่างปลอดภัย
 * - ทุกค่าคำนวณจากเวลาที่ Job เก็บไว้ (ฐาน logger.now() เดียวกับ log)
 * - Resource Wait เฉลี่ยคิดเฉพาะงานที่ใช้ resource; งาน NONE ถือเป็น 0 สำหรับรายงานต่อชิ้น
 */
public class Statistics {

    private int completed = 0;
    private long sumWaiting = 0;
    private long sumTurnaround = 0;
    private long sumResourceWait = 0;
    private int resourceJobs = 0;

    /** บันทึกว่างานชิ้นหนึ่งเสร็จแล้ว เรียกโดย Worker หลายตัวพร้อมกันได้ */
    public synchronized void recordCompletion(Job job) {
        completed++;
        sumWaiting += job.waitingMs();
        sumTurnaround += job.turnaroundMs();
        if (job.resource != ResourceType.NONE) {
            resourceJobs++;
            sumResourceWait += job.resourceWaitMs();
        }
    }

    /** จำนวนงานที่เสร็จแล้ว */
    public synchronized int completedCount() {
        return completed;
    }

    /** พิมพ์ตารางสรุปผลตอนจบโปรแกรม (เรียกหลังทุก Thread หยุดแล้ว) */
    public synchronized void printSummary(List<Job> allJobs, long makespanMs) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n===== SUMMARY =====\n");
        sb.append(String.format("%-5s %8s %8s %8s %9s %9s %8s %8s %8s%n",
                "job", "priority", "entered", "started", "waiting", "workReal", "resWait", "resHold", "turnarnd"));
        int violations = 0;
        for (Job j : allJobs) {
            if (!j.isCompleted()) {
                sb.append(String.format("%-5s NOT COMPLETED%n", j.id));
                continue;
            }
            sb.append(String.format("%-5s %8d %8d %8d %9d %9d %8d %8d %8d%n",
                    j.id, j.priority, j.enteredMs, j.startMs, j.waitingMs(),
                    j.actualWorkMs(), j.resourceWaitMs(), j.actualResourceHoldMs(), j.turnaroundMs()));
            // สมการตรวจสอบ (ใช้เวลาจริงของแต่ละช่วง)
            long sum = j.waitingMs() + j.actualWorkMs() + j.resourceWaitMs() + j.actualResourceHoldMs();
            if (sum != j.turnaroundMs()) {
                violations++;
            }
        }

        double throughput = makespanMs > 0 ? completed * 1000.0 / makespanMs : 0.0;
        sb.append("\n");
        sb.append(String.format("Completed jobs            : %d/%d%n", completed, allJobs.size()));
        sb.append(String.format("Makespan                  : %d ms%n", makespanMs));
        sb.append(String.format("Avg Waiting Time          : %d ms%n", avg(sumWaiting, completed)));
        sb.append(String.format("Avg Turnaround Time       : %d ms%n", avg(sumTurnaround, completed)));
        sb.append(String.format("Throughput                : %.2f jobs/s%n", throughput));
        sb.append(String.format("Avg Resource Wait Time    : %d ms (over %d jobs using resources)%n",
                avg(sumResourceWait, resourceJobs), resourceJobs));
        sb.append(String.format("Equation check            : Turnaround = Waiting + work + ResWait + hold -> %s%n",
                violations == 0 ? "OK (all jobs)" : violations + " job(s) MISMATCH"));
        System.out.print(sb);
        System.out.flush();
    }

    private static long avg(long sum, int count) {
        return count == 0 ? 0 : Math.round((double) sum / count);
    }
}

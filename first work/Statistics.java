import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * รวบรวมและคำนวณค่าวัดผล ปลอดภัยต่อหลาย Worker (synchronized บน lock เดียว)
 */
public class Statistics {

    private final Object lock = new Object();
    private final List<Job> completed = new ArrayList<>();

    /** บันทึกว่างานเสร็จแล้ว */
    public void recordCompletion(Job job) {
        synchronized (lock) {
            completed.add(job);
        }
    }

    public int completedCount() {
        synchronized (lock) {
            return completed.size();
        }
    }

    /** เวลาที่งานสุดท้ายเสร็จ (0 ถ้ายังไม่มี) */
    public long lastFinishMs() {
        synchronized (lock) {
            long max = 0;
            for (Job j : completed) {
                max = Math.max(max, j.finishMs);
            }
            return max;
        }
    }

    public void printSummary(List<Job> allJobs, long makespanMs) {
        List<Job> done;
        synchronized (lock) {
            done = new ArrayList<>(completed);
        }
        int n = done.size();
        long sumWait = 0, sumTurn = 0, sumRes = 0;
        int resJobs = 0;
        long maxDeviation = 0;
        String worstJob = "-";

        for (Job j : done) {
            sumWait += j.waitingMs();
            sumTurn += j.turnaroundMs();
            if (j.resource != ResourceType.NONE) {
                sumRes += j.resourceWaitMs;     // เฉลี่ยเฉพาะงานที่ใช้ resource
                resJobs++;
            }
            // สมการ: Turnaround = Waiting + workMs + ResourceWait + resourceMs
            long expected = j.waitingMs() + j.workMs + j.resourceWaitMs + j.resourceMs;
            long dev = Math.abs(j.turnaroundMs() - expected);
            if (dev > maxDeviation) {
                maxDeviation = dev;
                worstJob = j.id;
            }
        }

        long avgWait = n == 0 ? 0 : Math.round((double) sumWait / n);
        long avgTurn = n == 0 ? 0 : Math.round((double) sumTurn / n);
        long avgRes = resJobs == 0 ? 0 : Math.round((double) sumRes / resJobs);
        double throughput = makespanMs <= 0 ? 0.0 : n / (makespanMs / 1000.0);

        StringBuilder sb = new StringBuilder();
        sb.append("\n==================== SUMMARY ====================\n");
        sb.append(String.format(Locale.US, "Jobs completed        : %d / %d%n", n, allJobs.size()));
        sb.append(String.format(Locale.US, "Makespan              : %d ms%n", makespanMs));
        sb.append(String.format(Locale.US, "Avg Waiting Time      : %d ms%n", avgWait));
        sb.append(String.format(Locale.US, "Avg Turnaround Time   : %d ms%n", avgTurn));
        sb.append(String.format(Locale.US, "Throughput            : %.2f jobs/sec%n", throughput));
        sb.append(String.format(Locale.US, "Avg Resource Wait     : %d ms (เฉลี่ยจาก %d งานที่ใช้ resource)%n", avgRes, resJobs));
        sb.append(String.format(Locale.US, "Equation check        : max |T - (W+work+RW+res)| = %d ms (job %s)%n", maxDeviation, worstJob));
        sb.append("=================================================");
        System.out.println(sb);
    }
}

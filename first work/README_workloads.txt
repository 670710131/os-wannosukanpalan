Workloads for 517-312 Operating Systems Term Project

Files
jobs_standard.csv: Scheduling and worker-count experiments
jobs_printer.csv: Printer semaphore bottleneck experiment
jobs_db.csv: Database semaphore correctness test
jobs_same_priority.csv: Priority tie-break test
jobs_single.csv: Single-job graceful-shutdown edge case

Required experiment commands
java Main jobs_standard.csv fcfs 3 1 2
java Main jobs_standard.csv priority 3 1 2
java Main jobs_standard.csv priority 1 1 2
java Main jobs_standard.csv priority 5 1 2
java Main jobs_printer.csv priority 3 1 2
java Main jobs_printer.csv priority 3 2 2

For the nondeterminism comparison, run this baseline one additional time:
java Main jobs_standard.csv priority 3 1 2

CSV format
id,arrivalMs,priority,workMs,resource,resourceMs
Line endings: LF
When parsing, trim each field before numeric/enum conversion.

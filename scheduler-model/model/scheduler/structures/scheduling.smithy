$version: "2"

namespace com.nachiketa.scheduler

structure Schedule {
    @required
    type: ScheduleType

    @required
    expression: String
}

enum ScheduleType {
    ONE_TIME
    CRON
}

$version: "2"

namespace com.nachiketa.scheduler

string TaskId

structure Task {
    @required
    taskId: TaskId

    @required
    name: String

    @required
    parameterSchema: ParameterDefinitions
}

map ParameterDefinitions {
    key: String
    value: ParameterType
}

enum ParameterType {
    STRING
    INTEGER
    LONG
    BOOLEAN
    FLOAT
    DOUBLE
    TIMESTAMP
}

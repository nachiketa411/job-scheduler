$version: "2"

namespace com.nachiketa.scheduler

@error("client")
@httpError(404)
structure JobNotFoundException {
    @required
    message: String
}

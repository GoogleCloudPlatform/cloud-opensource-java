def buildLog = new File(basedir, "build.log")

// Profile "spring" should set the Spring Milestones repository, which has this reactor-bom.
assert !buildLog.text.contains(
    "Could not find artifact io.projectreactor:reactor-bom:pom:2020.0.0-M2 in central")

// io.projectreactor.netty:reactor-netty:jar:1.0.0-M2 is in the reactor-bom. It's available in
// Spring Milestones repository:
// https://repo.spring.io/milestone/io/projectreactor/netty/reactor-netty/1.0.0-M2/
assert !buildLog.text.contains(
    "io.projectreactor.netty:reactor-netty:jar:1.0.0-M2 was not resolved")

assert !buildLog.text.contains("NullPointerException")

assert buildLog.text.contains("Linkage Checker rule found 854 errors:")
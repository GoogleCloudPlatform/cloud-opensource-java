[JLBP-15] Avoid dependency and project cycles
---------------------------------------------

- Avoid cycles in artifact dependencies. If there is an initial temptation to
  add a cycle, instead lift up any shared types between the relevant artifacts
  into a new shared artifact.
- Avoid cycles in project dependencies (even if it doesn't technically create a
  cycle between artifacts).
  - Example violation: grpc-core depends on opencensus-api which depends on
    grpc-context, and grpc-core and grpc-context are always released together
    from the same repository. With this inter-project cycle, version propagation
    enters an infinite ping-pong. For example, when grpc-core and grpc-context
    are released at 1.20, then opencensus-api has to do a release to pick up the
    latest grpc-context (say 2.3 for example), then grpc-core and grpc-context
    need to be released again (1.21) to pick up the latest opencensus-api, and
    the back and forth continues forever.

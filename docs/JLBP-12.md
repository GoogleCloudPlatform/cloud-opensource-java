# [JLBP-12] Make level of support and API stability clear

Clearly document the lifecycle status and corresponding level of support
of the library. Examples include:

  - Incubating (no support expected)
  - Stable (SLOs in place to handle issues)
  - Maintenance (only bugs will be addressed)
  - Deprecated / Abandoned / Inactive (no work is expected)

Clearly state the API and behavioral stability clients can expect from the
library surface. Example statements:

  - This API is stable and suitable for production use, aside from packages,
    classes, and methods marked internal or unstable.
  - This API is beta and will not experience an API or behavior breaking change
    unless a bug or design flaw is uncovered.
  - This API is in development. Anything may change at any time. Stable products
    should not depend on it.

Document a feedback mechanism. For example:

  - File an issue at https://github.com/...
  - Send email to dev@fooproject.example.com

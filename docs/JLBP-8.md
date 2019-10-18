# [JLBP-8] Advance widely used functionality to a stable version

Libraries at 0.x versions and unstable features (which should be marked with
annotations if they live in a stable library, as outlined in
[JLBP-3](JLBP-3.md)) make no promises about stability. If they become widely
used, they can cause problems when there are surface breakages (which is allowed
per the rules of unstable functionality, explained in [JLBP-3](JLBP-3.md)). If
such breakages can cause widespread pain, the unstable features can become
"common-law GA." The preferable path is to promote such widely-used unstable
libraries and features to stability, even if the functionality is not complete.
Stability is not about completeness but about guarantees not to break
users' code frequently.

If the surface needs to change before it stabilizes, follow the process in
[JLBP-7](JLBP-7.md) to minimize disruption to the ecosystem. (TL;DR: Mark the old
surface `@Deprecated` and add the new surface in phase 1, delete the old surface
in phase 2). However, if it would take a long time for the ecosystem to complete
phase 2 (remove all references to the old surface), consider promoting the
library or feature to stable before phase 2 is completed because there is more
value to having a stable surface than there is to having a surface with zero
deprecated methods.

To avoid getting stuck in the first place, do not evangelize your pre-1.0 library
or suggest that other teams and projects depend on it.

- Be very clear with any teams and projects you work with that the library is unstable
  and should only be adopted if they are prepared to upgrade their own product regularly.
- Add an [unstable or experimental badge](https://github.com/badges/stability-badges)
  to your Github `README.md`.
- Consider not pushing a pre-1.0 artifact to the Central repository.

Once the API is stable and has reached 1.0 is the time to seek customers,
not before.

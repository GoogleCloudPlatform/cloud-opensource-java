[JLBP-8] Advance widely used functionality to a stable version
--------------------------------------------------------------

Libraries at 0.x versions and unstable features marked with
annotations make no promises about stability, and if they are widely
used, they can cause a lot of problems when there are surface
breakages (which is allowed per the rules of unstable functionality,
explained in [JLBP-3](JLBP-3.md)). If such breakages can cause
widespread pain, it can be seen as "common-law GA"; the preferable
path is to release the library as-is, with all its warts, maybe
marking less-desirable elements as deprecated.

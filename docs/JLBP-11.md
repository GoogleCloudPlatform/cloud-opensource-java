# [JLBP-11] Keep dependencies up to date

Release no later than 6 weeks after any of your dependencies
releases a higher version. Time is important, not just how many versions behind,
because a dependency tree that is up to date at each point in time is
more likely to be internally consistent and compatible.
Staying up to date is also important to ensure that security fixes are rolled
out promptly.

There are several tools that make upgrading dependencies easier by performing much of the grunt work:

* The [Versions Maven Plugin](https://www.mojohaus.org/versions-maven-plugin/)
  can inform you of new versions of dependencies or rewrite a
  pom.xml to use the new versions.
* [Renovate](https://renovate.whitesourcesoftware.com/) notices when a new
  version of a dependency is released and files a pull or merge request to
  update your repository.
* GitHub's [Dependabot](https://dependabot.com/) creates pull requests to address security advisories and dependency upgrades.

These tools assume that the new versions are compatible with the older versions.
In cases where they're not fully compatible, a dependency upgrade can still require
manual attention to rewrite and update affected code. However these tools do remove
much of the pain from small updates.

If your library does not have the investment necessary to keep up to date with
dependencies, advise consumers to move to a library that is kept more up to
date.

<!DOCTYPE html>
<html lang="en-US">
  <#include "macros.ftl">
  <head>
    <meta charset="utf-8" />
    <title>Google Cloud Platform Java Open Source Dependency Dashboard Artifact Details Table</title>
    <link rel="stylesheet" href="dashboard.css" />
    <script src="dashboard.js"></script>
  </head>
  <body>
    <h1>Google Cloud Platform Java Dependency Dashboard Artifact Details Table</h1>

    <h2>Artifact Details</h2>

    <table class="dependency-dashboard">
      <tr>
        <th>Artifact</th>
        <th title=
                "Linkage check result for the artifact and transitive dependencies. PASS means all symbol references have valid referents.">
          Linkage Check</th>
        <th title=
          "For each transitive dependency the library pulls in, the highest version found anywhere in the dependency tree is picked.">
          Upper Bounds</th>
        <th title=
          "For each transitive dependency the library pulls in, the highest version found anywhere in the union of the BOM's dependency trees is picked.">
          Global Upper Bounds</th>
        <th title=
                "There is exactly one version of each dependency in the library's transitive dependency tree. That is, two artifacts with the same group ID and artifact ID but different versions do not appear in the tree. No dependency mediation is necessary.">
          Dependency Convergence</th>
      </tr>
      <#list table as row>
        <#assign report_url = row.getCoordinates()?replace(":", "_") + '.html' />
        <tr>
          <td class="artifact-name"><a href='${report_url}'>${row.getCoordinates()}</a></td>
          <#-- The name key should match TEST_NAME_XXXX variables -->
          <@testResult row=row name="Linkage Errors"/>
          <@testResult row=row name="Upper Bounds"/>
          <@testResult row=row name="Global Upper Bounds"/>
          <@testResult row=row name="Dependency Convergence"/>
        </tr>
      </#list>
    </table>
    
    <hr />

    <h2>Linkage Errors</h2>

    <#list coordinatesToProblems as symbolProblem>
      <@formatJarLinkageReport jarLinkageReport jarToDependencyPaths dependencyPathRootCauses/>
    </#list>

    <hr />
    <p id='updated'>Last generated at ${lastUpdated}</p>
  </body>
</html>